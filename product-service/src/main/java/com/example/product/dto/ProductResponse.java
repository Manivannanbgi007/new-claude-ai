package com.example.product.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * What clients receive. Deliberately separate from the Product document so the
 * index structure can change without breaking the public API.
 * version = the source-of-truth version; send it back with an update (PUT).
 */
public record ProductResponse(
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
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
