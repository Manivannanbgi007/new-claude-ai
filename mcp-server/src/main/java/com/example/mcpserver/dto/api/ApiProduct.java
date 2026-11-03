package com.example.mcpserver.dto.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A product as returned by the product API (through the gateway).
 * This is this service's OWN copy of the contract; DTOs are never shared
 * between services, so each service can evolve independently.
 */
public record ApiProduct(
        String id,
        String sku,
        String name,
        String description,
        String brand,
        String category,
        BigDecimal price,
        Integer ramGb,
        Double rating,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt) {
}
