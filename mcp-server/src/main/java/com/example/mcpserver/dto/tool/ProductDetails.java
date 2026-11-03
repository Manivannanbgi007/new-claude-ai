package com.example.mcpserver.dto.tool;

import java.math.BigDecimal;
import java.util.List;

/** Full product information for the getProductById tool. */
public record ProductDetails(
        String id,
        String sku,
        String name,
        String description,
        String brand,
        String category,
        BigDecimal priceUsd,
        Integer ramGb,
        Double rating,
        List<String> tags) {
}
