package com.example.mcpserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Typed access to app.product-api.* in application.yml.
 * Defaults apply when a property is missing.
 */
@ConfigurationProperties(prefix = "app.product-api")
public record ProductApiProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

    public ProductApiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(20);
        }
    }
}
