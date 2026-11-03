package com.example.mcpserver.model;

import java.math.BigDecimal;

/**
 * Search parameters exactly as the LLM sent them (may contain nulls, blanks
 * or odd values). The service cleans them up before calling the API.
 */
public record ProductSearchParams(
        String query,
        String category,
        String brand,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer minRamGb,
        Integer maxRamGb,
        String sortBy,
        String sortDirection,
        Integer page,
        Integer size) {
}
