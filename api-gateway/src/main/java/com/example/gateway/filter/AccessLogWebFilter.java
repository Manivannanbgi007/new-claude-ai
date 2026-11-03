package com.example.gateway.filter;

import com.example.gateway.util.GatewayConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Logs one line per request, for example:
 *   GET /api/v1/products -> 200 (35 ms) [3f2a9c1e-...] user=7c9e6679-...
 * "user" is the X-User-Id header, so every request can be traced to a user.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)   // right after CorrelationIdWebFilter
public class AccessLogWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(AccessLogWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startNanos = System.nanoTime();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getRawPath();
        String userId = exchange.getRequest().getHeaders().getFirst(GatewayConstants.USER_ID_HEADER);

        // beforeCommit runs just before the response is sent - also for error responses,
        // so the final status code (e.g. 503 from the error handler) is always known here.
        exchange.getResponse().beforeCommit(() -> {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            String correlationId = exchange.getAttribute(GatewayConstants.CORRELATION_ID_ATTRIBUTE);
            int statusCode = status != null ? status.value() : 0;

            // Reactive code has no per-request MDC, so the correlation id is put into the MDC
            // just for THIS log call. Same field name as in every other service, so one request
            // can be followed across all logs (e.g. later in Loki: {correlationId="..."}).
            if (correlationId != null) {
                MDC.put(GatewayConstants.CORRELATION_ID_ATTRIBUTE, correlationId);
            }
            try {
                // Key-value pairs become separate JSON fields in the structured log file;
                // the console still shows the readable message.
                log.atInfo()
                        .addKeyValue("httpMethod", method)
                        .addKeyValue("httpPath", path)
                        .addKeyValue("httpStatus", statusCode)
                        .addKeyValue("durationMs", durationMs)
                        .addKeyValue("userId", userId != null ? userId : "-")
                        .log("{} {} -> {} ({} ms) [{}] user={}", method, path,
                                statusCode != 0 ? statusCode : "-", durationMs, correlationId,
                                userId != null ? userId : "-");
            } finally {
                MDC.remove(GatewayConstants.CORRELATION_ID_ATTRIBUTE);
            }
            return Mono.empty();
        });

        return chain.filter(exchange);
    }
}
