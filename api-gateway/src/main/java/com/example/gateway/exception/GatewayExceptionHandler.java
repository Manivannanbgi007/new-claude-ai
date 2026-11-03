package com.example.gateway.exception;

import com.example.gateway.util.GatewayConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Converts errors that happen INSIDE the gateway into the same ProblemDetail JSON
 * format that product-service uses. Examples:
 *   - no route for the path                    -> 404
 *   - service not registered in Eureka         -> 503
 *   - service registered but not reachable     -> 503
 *   - service too slow (response-timeout)      -> 504
 *
 * Errors returned BY a backend service (e.g. product 404) are not touched:
 * the gateway passes those responses through unchanged.
 *
 * Order -2 runs before Spring Boot's default error handler (order -1).
 */
@Component
@Order(-2)
public class GatewayExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    private final List<HttpMessageWriter<?>> messageWriters;

    public GatewayExceptionHandler(ServerCodecConfigurer codecConfigurer) {
        this.messageWriters = codecConfigurer.getWriters();
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            // Part of the response was already sent; we cannot replace it anymore.
            return Mono.error(ex);
        }

        if (ex instanceof GatewayRejectionException rejection) {
            return writeRejection(exchange, rejection);
        }

        HttpStatus status = resolveStatus(ex);
        String correlationId = exchange.getAttribute(GatewayConstants.CORRELATION_ID_ATTRIBUTE);
        String path = exchange.getRequest().getURI().getRawPath();

        if (status.is5xxServerError()) {
            log.error("Gateway error {} for {} [{}]: {}", status.value(), path, correlationId, ex.toString());
        } else {
            log.warn("Gateway error {} for {} [{}]", status.value(), path, correlationId);
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detailFor(status));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(path));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("correlationId", correlationId);

        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(problem)
                .flatMap(response -> response.writeTo(exchange, new ResponseContext(messageWriters)));
    }

    /** A deliberate refusal by the gateway (bulkhead full ...): its own title/detail + Retry-After. */
    private Mono<Void> writeRejection(ServerWebExchange exchange, GatewayRejectionException rejection) {
        String path = exchange.getRequest().getURI().getRawPath();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(rejection.getStatus(), rejection.getMessage());
        problem.setTitle(rejection.getTitle());
        problem.setInstance(URI.create(path));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("correlationId", exchange.getAttribute(GatewayConstants.CORRELATION_ID_ATTRIBUTE));

        ServerResponse.BodyBuilder response = ServerResponse.status(rejection.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (rejection.getRetryAfter() != null) {
            response.header("Retry-After", String.valueOf(rejection.getRetryAfter().toSeconds()));
        }
        return response.bodyValue(problem)
                .flatMap(r -> r.writeTo(exchange, new ResponseContext(messageWriters)));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
            return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        // Walk the cause chain: "connection refused" is often wrapped in other exceptions.
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConnectException) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /** Client-friendly messages; technical details go to the log only. */
    private String detailFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "No API exists at this path.";
            case SERVICE_UNAVAILABLE -> "The requested service is temporarily unavailable. Please try again later.";
            case GATEWAY_TIMEOUT -> "The requested service did not respond in time.";
            default -> status.is4xxClientError() ? "The request could not be processed."
                    : "An unexpected gateway error occurred.";
        };
    }

    /** Tells ServerResponse which JSON writers to use (the ones Spring Boot configured). */
    private record ResponseContext(List<HttpMessageWriter<?>> messageWriters) implements ServerResponse.Context {

        @Override
        public List<ViewResolver> viewResolvers() {
            return List.of();
        }
    }
}
