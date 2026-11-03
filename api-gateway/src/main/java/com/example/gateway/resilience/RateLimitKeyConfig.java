package com.example.gateway.resilience;

import com.example.gateway.util.GatewayConstants;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.regex.Pattern;

/**
 * WHO a rate limit applies to. Each key gets its own token bucket per route.
 *
 *   X-User-Id present (valid UUID)  -> "user:<id>"   one bucket per user
 *   otherwise                        -> "ip:<address>" one bucket per client address
 *
 * Today X-User-Id is set by trusted callers (mcp-server); with security the gateway
 * sets it from the validated JWT, so users cannot fake another user's bucket.
 * Behind a load balancer, use the client IP from X-Forwarded-For of THAT trusted proxy.
 */
@Configuration
public class RateLimitKeyConfig {

    private static final Pattern UUID = Pattern.compile("^[0-9a-fA-F-]{36}$");

    @Bean
    public KeyResolver clientKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(GatewayConstants.USER_ID_HEADER);
            if (userId != null && UUID.matcher(userId).matches()) {
                return Mono.just("user:" + userId.toLowerCase());
            }
            InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
            String ip = remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : "unknown";
            return Mono.just("ip:" + ip);
        };
    }
}
