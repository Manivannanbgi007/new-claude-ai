package com.example.product.dto;

import java.util.List;

/**
 * Which categories and brands exist, with product counts.
 * Useful for filter menus - and for the AI chatbot to know valid filter values.
 */
public record FacetResponse(List<FacetBucket> categories, List<FacetBucket> brands) {
}
