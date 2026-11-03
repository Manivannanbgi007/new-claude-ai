package com.example.gateway.filter;

import com.example.gateway.exception.GatewayRejectionException;
import com.example.gateway.resilience.BulkheadRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BULKHEAD route filter: limits how many requests to ONE route may be in flight at the
 * same time. Like the watertight compartments of a ship: if the chatbot (slow LLM) is
 * flooded, only the chatbot's compartment fills up - product search, stock and orders
 * keep their own capacity.
 *
 * Usage in application.yml (name, maxConcurrentCalls):
 *     - Bulkhead=chatbot, 5
 *
 * A request that finds the bulkhead full is rejected IMMEDIATELY with 503 + Retry-After
 * (no queueing: waiting requests would hold connections and memory, which is exactly
 * what a bulkhead must prevent). Rejections are NOT counted by the circuit breaker,
 * because the bulkhead runs before it - the backend did nothing wrong.
 */
@Component
public class BulkheadGatewayFilterFactory extends AbstractGatewayFilterFactory<BulkheadGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(BulkheadGatewayFilterFactory.class);

    private final BulkheadRegistry registry;

    public BulkheadGatewayFilterFactory(BulkheadRegistry registry) {
        super(Config.class);
        this.registry = registry;
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("name", "maxConcurrentCalls");
    }

    @Override
    public GatewayFilter apply(Config config) {
        BulkheadRegistry.Bulkhead bulkhead = registry.getOrCreate(config.getName(), config.getMaxConcurrentCalls());

        return (exchange, chain) -> {
            if (!bulkhead.permits().tryAcquire()) {
                long rejected = bulkhead.rejectedCalls().incrementAndGet();
                log.warn("Bulkhead '{}' full ({} calls in flight) - rejected (total rejected: {})",
                        bulkhead.name(), bulkhead.maxConcurrentCalls(), rejected);
                return Mono.error(new GatewayRejectionException(HttpStatus.SERVICE_UNAVAILABLE, "Service busy",
                        "Too many requests are being processed by this service right now. Please retry shortly.",
                        Duration.ofSeconds(2)));
            }
            // Release exactly once when the request ends: success, error OR client disconnect.
            AtomicBoolean released = new AtomicBoolean(false);
            return Mono.defer(() -> chain.filter(exchange))
                    .doFinally(signal -> {
                        if (released.compareAndSet(false, true)) {
                            bulkhead.permits().release();
                        }
                    });
        };
    }

    public static class Config {

        /** Shown in logs and /actuator/resilience - use the route id. */
        private String name = "default";

        /** Max requests in flight at the same time for this route. */
        private int maxConcurrentCalls = 50;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getMaxConcurrentCalls() {
            return maxConcurrentCalls;
        }

        public void setMaxConcurrentCalls(int maxConcurrentCalls) {
            this.maxConcurrentCalls = maxConcurrentCalls;
        }
    }
}
