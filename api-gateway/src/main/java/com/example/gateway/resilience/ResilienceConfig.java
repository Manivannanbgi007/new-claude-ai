package com.example.gateway.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Turns app.resilience.* (YAML) into Resilience4j circuit breakers + time limiters.
 * The CircuitBreaker filter of each route looks up its breaker by NAME
 * (e.g. "product-read"); unknown names get the defaults.
 *
 * The TIME LIMITER is the per-route timeout: when it fires, the request is cancelled
 * and the route's fallback answers. Resilience4j's own default is only 1 second -
 * far too short for some routes - so every breaker gets an explicit timeout here.
 */
@Configuration
@EnableConfigurationProperties(ResilienceProperties.class)
public class ResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> circuitBreakerCustomizer(ResilienceProperties properties) {
        ResilienceProperties.CircuitBreakerSettings defaults = properties.circuitBreakerDefaults();
        return factory -> {
            factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                    .circuitBreakerConfig(circuitBreakerConfig(defaults))
                    .timeLimiterConfig(timeLimiterConfig(defaults))
                    .build());

            properties.circuitBreakers().forEach((name, overrides) -> {
                ResilienceProperties.CircuitBreakerSettings settings = overrides.withDefaultsFrom(defaults);
                factory.configure(builder -> builder
                        .circuitBreakerConfig(circuitBreakerConfig(settings))
                        .timeLimiterConfig(timeLimiterConfig(settings)), name);
                log.info("Circuit breaker '{}': timeout={}, failureRate>={}%, open for {}",
                        name, settings.timeout(), settings.failureRateThreshold(), settings.waitDurationInOpenState());
            });
        };
    }

    private static CircuitBreakerConfig circuitBreakerConfig(ResilienceProperties.CircuitBreakerSettings s) {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(s.slidingWindowSize())
                .minimumNumberOfCalls(s.minimumNumberOfCalls())
                .failureRateThreshold(s.failureRateThreshold())
                .slowCallRateThreshold(s.slowCallRateThreshold())
                .slowCallDurationThreshold(s.slowCallDurationThreshold())
                .waitDurationInOpenState(s.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(s.permittedCallsInHalfOpenState())
                // OPEN -> HALF_OPEN happens on a timer, not only when the next request arrives
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
    }

    private static TimeLimiterConfig timeLimiterConfig(ResilienceProperties.CircuitBreakerSettings s) {
        return TimeLimiterConfig.custom().timeoutDuration(s.timeout()).build();
    }
}
