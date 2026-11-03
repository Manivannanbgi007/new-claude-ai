package com.example.gateway.resilience;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * All bulkheads by name - shared by the Bulkhead filter and the /actuator/resilience endpoint.
 *
 * The bean is named "gatewayBulkheadRegistry" on purpose: Resilience4j's auto-configuration
 * (pulled in by spring-cloud-starter-circuitbreaker-reactor-resilience4j) already registers
 * a bean called "bulkheadRegistry", and bean overriding is disabled by default.
 */
@Component("gatewayBulkheadRegistry")
public class BulkheadRegistry {

    /** One bulkhead: at most maxConcurrentCalls requests in flight at the same time. */
    public record Bulkhead(String name, int maxConcurrentCalls, Semaphore permits, AtomicLong rejectedCalls) {

        public int callsInFlight() {
            return maxConcurrentCalls - permits.availablePermits();
        }
    }

    private final ConcurrentMap<String, Bulkhead> bulkheads = new ConcurrentHashMap<>();

    /** Returns the existing bulkhead, or a new one if it doesn't exist or its limit changed. */
    public Bulkhead getOrCreate(String name, int maxConcurrentCalls) {
        return bulkheads.compute(name, (key, existing) ->
                existing != null && existing.maxConcurrentCalls() == maxConcurrentCalls
                        ? existing
                        : new Bulkhead(name, maxConcurrentCalls, new Semaphore(maxConcurrentCalls), new AtomicLong()));
    }

    public Collection<Bulkhead> all() {
        return List.copyOf(bulkheads.values());
    }
}
