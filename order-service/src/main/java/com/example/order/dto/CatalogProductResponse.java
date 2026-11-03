package com.example.order.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** A product as order-service currently knows it (local copy of the catalogue). */
public record CatalogProductResponse(String sku, String productId, String name, String brand, String category,
                                     BigDecimal price, String currency, Instant updatedAt) {
}
