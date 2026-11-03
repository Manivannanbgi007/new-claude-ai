package com.example.product.model;

import com.example.product.util.ProductFields;

/**
 * The only fields clients may sort by (a whitelist).
 * Each value knows its Elasticsearch field and its natural default direction.
 */
public enum ProductSortField {

    /** Best text match first. Only meaningful together with a search text. */
    RELEVANCE("_score", SortDirection.DESC),
    PRICE(ProductFields.PRICE, SortDirection.ASC),
    NAME(ProductFields.NAME_KEYWORD, SortDirection.ASC),
    RATING(ProductFields.RATING, SortDirection.DESC),
    RAM(ProductFields.RAM_GB, SortDirection.DESC),
    NEWEST(ProductFields.CREATED_AT, SortDirection.DESC);

    private final String field;
    private final SortDirection defaultDirection;

    ProductSortField(String field, SortDirection defaultDirection) {
        this.field = field;
        this.defaultDirection = defaultDirection;
    }

    public String getField() {
        return field;
    }

    public SortDirection getDefaultDirection() {
        return defaultDirection;
    }
}
