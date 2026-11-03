package com.example.productwrite.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** app.outbox.* in application.yml. */
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(Integer batchSize, Duration sendTimeout, Duration retention) {

    public OutboxProperties {
        if (batchSize == null || batchSize < 1) {
            batchSize = 100;
        }
        if (sendTimeout == null) {
            sendTimeout = Duration.ofSeconds(10);
        }
        if (retention == null) {
            retention = Duration.ofDays(7);
        }
    }
}
