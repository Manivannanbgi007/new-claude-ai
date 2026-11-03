package com.example.mcpserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * app.purchase.* in application.yml.
 *
 * customerId: the chatbot has no login yet, so all chatbot orders are placed for ONE
 * configured (fake) customer. In the security phase this is replaced by the id of the
 * logged-in user.
 */
@ConfigurationProperties(prefix = "app.purchase")
public record PurchaseProperties(
        String customerId,
        Integer maxQuantity,
        Duration confirmationWait,
        Duration pollInterval) {

    public PurchaseProperties {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalStateException("app.purchase.customer-id must be configured");
        }
        if (maxQuantity == null || maxQuantity < 1) {
            maxQuantity = 10;
        }
        if (confirmationWait == null) {
            confirmationWait = Duration.ofSeconds(5);
        }
        if (pollInterval == null) {
            pollInterval = Duration.ofMillis(300);
        }
    }
}
