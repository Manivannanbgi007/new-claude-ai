package com.example.stock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * app.kafka.error-handling.* from application.yml.
 * Change the retry timing or the DLT name there - no Java change needed.
 */
@ConfigurationProperties(prefix = "app.kafka.error-handling")
public record KafkaErrorHandlingProperties(
        Duration initialInterval,
        Double multiplier,
        Duration maxInterval,
        String dltSuffix) {

    /** Safe defaults if a property is missing from the YAML. */
    public KafkaErrorHandlingProperties {
        if (initialInterval == null) {
            initialInterval = Duration.ofSeconds(1);
        }
        if (multiplier == null || multiplier < 1.0) {
            multiplier = 2.0;
        }
        if (maxInterval == null) {
            maxInterval = Duration.ofSeconds(30);
        }
        if (dltSuffix == null || dltSuffix.isBlank()) {
            dltSuffix = ".dlt";
        }
    }
}
