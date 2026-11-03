package com.example.product.model;

/**
 * Search input used by the repository layer.
 * It is independent of the web layer: defaults are already resolved and
 * prices are plain doubles, ready for Elasticsearch.
 */
public record ProductSearchCriteria(
        String text,
        String brand,
        String category,
        Double minPrice,
        Double maxPrice,
        Integer minRamGb,
        Integer maxRamGb,
        Double minRating,
        String tag,
        ProductSortField sortBy,
        SortDirection sortDirection,
        int page,
        int size) {
}
