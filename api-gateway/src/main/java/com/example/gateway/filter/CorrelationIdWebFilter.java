package com.example.gateway.filter;

import com.example.gateway.util.GatewayConstants;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Makes sure every request has an X-Correlation-Id:
 * reuses a valid incoming one, otherwise creates a new one.
 * The id is forwarded to the backend service and returned to the client.
 *
 * A WebFilter (instead of a gateway GlobalFilter) runs for EVERY request,
 * including requests that match no route, so even 404 errors get an id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    /** Only accept safe values, so callers cannot inject fake lines into our logs. */
    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(GatewayConstants.CORRELATION_ID_HEADER);
        String correlationId = (incoming != null && VALID_ID.matcher(incoming).matches())
                ? incoming
                : UUID.randomUUID().toString();

        // Requests are immutable in WebFlux, so we build a modified copy
        // that carries the (possibly new) header to the backend service.
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(GatewayConstants.CORRELATION_ID_HEADER, correlationId))
                .build();

        exchange.getAttributes().put(GatewayConstants.CORRELATION_ID_ATTRIBUTE, correlationId);
        exchange.getResponse().getHeaders().set(GatewayConstants.CORRELATION_ID_HEADER, correlationId);

        // Continue the chain with the modified request.
        return chain.filter(exchange.mutate().request(request).build());
    }
}
