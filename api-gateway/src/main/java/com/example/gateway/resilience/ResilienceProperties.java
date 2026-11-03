package com.example.gateway.resilience;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * app.resilience.* in application.yml: circuit breaker and timeout values.
 *
 *   circuit-breaker-defaults  apply to every circuit breaker
 *   circuit-breakers.<name>   override single values for one breaker (the name used
 *                             in the route's CircuitBreaker filter, e.g. "chatbot")
 */
@ConfigurationProperties(prefix = "app.resilience")
public record ResilienceProperties(CircuitBreakerSettings circuitBreakerDefaults,
                                   Map<String, CircuitBreakerSettings> circuitBreakers) {

    public ResilienceProperties {
        if (circuitBreakerDefaults == null) {
            circuitBreakerDefaults = CircuitBreakerSettings.SAFE_DEFAULTS;
        } else {
            circuitBreakerDefaults = circuitBreakerDefaults.withDefaultsFrom(CircuitBreakerSettings.SAFE_DEFAULTS);
        }
        circuitBreakers = circuitBreakers == null ? Map.of() : Map.copyOf(circuitBreakers);
    }

    /**
     * Settings of one circuit breaker. Any value left out in YAML is taken from the defaults.
     *
     *   slidingWindowSize            how many recent calls are evaluated
     *   minimumNumberOfCalls         no decision before this many calls (avoids opening after 1 error)
     *   failureRateThreshold         % failed calls that OPEN the circuit
     *   slowCallRateThreshold        % slow calls that OPEN the circuit
     *   slowCallDurationThreshold    a call slower than this counts as "slow"
     *   waitDurationInOpenState      how long the circuit stays OPEN before testing again
     *   permittedCallsInHalfOpenState test calls allowed in HALF_OPEN
     *   timeout                      max time for one request (incl. retries) -> fallback
     */
    public record CircuitBreakerSettings(
            Integer slidingWindowSize,
            Integer minimumNumberOfCalls,
            Float failureRateThreshold,
            Float slowCallRateThreshold,
            Duration slowCallDurationThreshold,
            Duration waitDurationInOpenState,
            Integer permittedCallsInHalfOpenState,
            Duration timeout) {

        static final CircuitBreakerSettings SAFE_DEFAULTS = new CircuitBreakerSettings(
                20, 10, 50f, 80f, Duration.ofSeconds(3), Duration.ofSeconds(15), 5, Duration.ofSeconds(5));

        /** Fills every null field from "defaults". */
        public CircuitBreakerSettings withDefaultsFrom(CircuitBreakerSettings defaults) {
            return new CircuitBreakerSettings(
                    slidingWindowSize != null ? slidingWindowSize : defaults.slidingWindowSize(),
                    minimumNumberOfCalls != null ? minimumNumberOfCalls : defaults.minimumNumberOfCalls(),
                    failureRateThreshold != null ? failureRateThreshold : defaults.failureRateThreshold(),
                    slowCallRateThreshold != null ? slowCallRateThreshold : defaults.slowCallRateThreshold(),
                    slowCallDurationThreshold != null ? slowCallDurationThreshold : defaults.slowCallDurationThreshold(),
                    waitDurationInOpenState != null ? waitDurationInOpenState : defaults.waitDurationInOpenState(),
                    permittedCallsInHalfOpenState != null ? permittedCallsInHalfOpenState : defaults.permittedCallsInHalfOpenState(),
                    timeout != null ? timeout : defaults.timeout());
        }
    }
}
