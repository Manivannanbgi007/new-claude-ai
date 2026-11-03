package com.example.productwrite.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** The committed state in MySQL. version is needed for the next update. */
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
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
