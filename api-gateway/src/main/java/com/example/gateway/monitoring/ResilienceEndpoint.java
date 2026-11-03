package com.example.gateway.monitoring;

import com.example.gateway.resilience.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /actuator/resilience - live state of every circuit breaker and bulkhead.
 * Circuit breakers appear after their route received its first request.
 */
@Component
@Endpoint(id = "resilience")
public class ResilienceEndpoint {

    private final ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistry;
    private final BulkheadRegistry bulkheadRegistry;

    public ResilienceEndpoint(ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistry,
                              BulkheadRegistry bulkheadRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
    }

    @ReadOperation
    public Map<String, Object> resilience() {
        Map<String, Object> result = new LinkedHashMap<>();
        CircuitBreakerRegistry registry = circuitBreakerRegistry.getIfAvailable();
        result.put("circuitBreakers", registry == null ? List.of() : registry.getAllCircuitBreakers().stream()
                .sorted(Comparator.comparing(CircuitBreaker::getName))
                .map(ResilienceEndpoint::describe)
                .toList());
        result.put("bulkheads", bulkheadRegistry.all().stream()
                .sorted(Comparator.comparing(BulkheadRegistry.Bulkhead::name))
                .map(b -> Map.of(
                        "name", b.name(),
                        "maxConcurrentCalls", b.maxConcurrentCalls(),
                        "callsInFlight", b.callsInFlight(),
                        "rejectedCalls", b.rejectedCalls().get()))
                .toList());
        return result;
    }

    private static Map<String, Object> describe(CircuitBreaker cb) {
        CircuitBreaker.Metrics m = cb.getMetrics();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", cb.getName());
        info.put("state", cb.getState().name());               // CLOSED / OPEN / HALF_OPEN
        info.put("failureRatePercent", m.getFailureRate());    // -1 = not enough calls yet
        info.put("slowCallRatePercent", m.getSlowCallRate());
        info.put("bufferedCalls", m.getNumberOfBufferedCalls());
        info.put("failedCalls", m.getNumberOfFailedCalls());
        info.put("notPermittedCalls", m.getNumberOfNotPermittedCalls());
        return info;
    }
}
