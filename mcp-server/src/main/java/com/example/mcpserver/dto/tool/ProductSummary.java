package com.example.mcpserver.dto.tool;

import java.math.BigDecimal;

/**
 * Compact product for search results: only what the chatbot needs to answer.
 * No description or timestamps - fewer tokens means faster, cheaper, more
 * accurate answers from a small local model.
 */
public record ProductSummary(
        String id,
        /** Needed for checkProductStock (stock is kept per SKU). */
        String sku,
        String name,
        String brand,
        String category,
        BigDecimal priceUsd,
        Integer ramGb,
        Double rating) {
}
