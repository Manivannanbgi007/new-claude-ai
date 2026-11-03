package com.example.gateway.controller;

import com.example.gateway.util.GatewayConstants;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FALLBACK target of every CircuitBreaker route filter (fallbackUri: forward:/fallback/<name>).
 * Called when the circuit is OPEN, the request TIMED OUT, or the backend FAILED.
 *
 *   product-read + GET /api/v1/products/{id}
 *       -> FALLBACK ROUTING: read the product from product-write-service (MySQL, the
 *          source of truth) instead of product-service (Elasticsearch read model).
 *   everything else
 *       -> a clear 503 ProblemDetail with the reason and Retry-After.
 *
 * Writes are NEVER re-sent here: if a POST timed out, the backend may already have done
 * it, so the answer says "result unknown" instead of guessing.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    private static final Pattern PRODUCT_BY_ID = Pattern.compile("^/api/v1/products/([0-9a-fA-F-]{36})$");
    private static final Duration RETRY_AFTER = Duration.ofSeconds(15);
    private static final Duration ALTERNATIVE_ROUTE_TIMEOUT = Duration.ofSeconds(5);

    /** What a user should know when this part of the platform is unavailable. */
    private static final Map<String, String> MESSAGES = Map.of(
            "product-read", "Product search is temporarily unavailable. Please try again shortly.",
            "product-write", "Product changes can't be processed right now.",
            "stock", "Stock information is temporarily unavailable.",
            "orders", "The order service is temporarily unavailable.",
            "users", "The user service is temporarily unavailable.",
            "rag", "The document knowledge base is temporarily unavailable.",
            "chatbot", "The assistant is temporarily unavailable. Please try again in a moment.");

    private final WebClient loadBalancedWebClient;

    /** ReactorLoadBalancerExchangeFilterFunction resolves "http://product-write-service" through Eureka. */
    public FallbackController(ReactorLoadBalancerExchangeFilterFunction loadBalancer) {
        this.loadBalancedWebClient = WebClient.builder().filter(loadBalancer).build();
    }

    @RequestMapping("/{name}")
    public Mono<ResponseEntity<Object>> fallback(@PathVariable String name, ServerWebExchange exchange) {
        URI original = originalRequestUri(exchange);
        if (original == null) {
            // Called directly from outside, not by a circuit breaker: behave like an unknown path.
            return Mono.just(ResponseEntity.notFound().build());
        }
        HttpMethod method = exchange.getRequest().getMethod();
        Throwable cause = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        String reason = reasonOf(cause);
        String correlationId = exchange.getAttribute(GatewayConstants.CORRELATION_ID_ATTRIBUTE);
        log.warn("Fallback '{}' for {} {} - reason {} ({}) [{}]", name, method, original.getPath(), reason,
                cause == null ? "-" : cause.getClass().getSimpleName(), correlationId);

        Matcher productById = PRODUCT_BY_ID.matcher(original.getPath());
        if ("product-read".equals(name) && HttpMethod.GET.equals(method) && productById.matches()) {
            return readProductFromWriteSide(productById.group(1), correlationId)
                    .onErrorResume(ex -> {
                        log.warn("Alternative route for product {} failed too: {}", productById.group(1), ex.getMessage());
                        return Mono.just(problem(name, reason, original.getPath(), method, correlationId));
                    });
        }
        return Mono.just(problem(name, reason, original.getPath(), method, correlationId));
    }

    /** Fallback routing: same product, other source. The response says where it came from. */
    private Mono<ResponseEntity<Object>> readProductFromWriteSide(String productId, String correlationId) {
        return loadBalancedWebClient.get()
                .uri("http://product-write-service/api/v1/products/{id}", productId)
                .header(GatewayConstants.CORRELATION_ID_HEADER, correlationId == null ? "" : correlationId)
                .retrieve()
                .toEntity(byte[].class)
                .timeout(ALTERNATIVE_ROUTE_TIMEOUT)
                .map(entity -> ResponseEntity.status(entity.getStatusCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        // Tells clients (and you, when testing) that a fallback route answered
                        .header("X-Fallback-Source", "product-write-service")
                        .body((Object) entity.getBody()));
    }

    private static ResponseEntity<Object> problem(String name, String reason, String path, HttpMethod method,
                                                  String correlationId) {
        String detail = MESSAGES.getOrDefault(name, "The requested service is temporarily unavailable.");
        boolean isWrite = !HttpMethod.GET.equals(method) && !HttpMethod.HEAD.equals(method);
        if (isWrite && "TIMEOUT".equals(reason)) {
            // A timed-out write may have been executed anyway - never claim it failed.
            detail += " The request took too long: it MAY have been processed. Check the result before retrying"
                    + ("orders".equals(name) ? ", and retry with the same Idempotency-Key so no duplicate order is created." : ".");
        } else if (isWrite) {
            detail += " Nothing was changed by this request.";
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, detail);
        problem.setTitle("Service temporarily unavailable");
        problem.setInstance(URI.create(path));
        problem.setProperty("service", name);
        problem.setProperty("reason", reason);
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("correlationId", correlationId);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(RETRY_AFTER.toSeconds()))
                .body((Object) problem);
    }

    /**
     *   CIRCUIT_OPEN    the breaker is open: the backend was not even called (fast failure)
     *   TIMEOUT         the backend did not answer within the breaker's timeout
     *   BACKEND_ERROR   the backend answered 5xx or the connection failed
     */
    private static String reasonOf(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof CallNotPermittedException) {
                return "CIRCUIT_OPEN";
            }
            if (t instanceof TimeoutException) {
                return "TIMEOUT";
            }
        }
        return "BACKEND_ERROR";
    }

    @SuppressWarnings("unchecked")
    private static URI originalRequestUri(ServerWebExchange exchange) {
        Set<URI> uris = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        return uris == null || uris.isEmpty() ? null : uris.iterator().next();
    }
}
