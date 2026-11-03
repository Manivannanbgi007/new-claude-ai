package com.example.product.util;

/**
 * Elasticsearch field names of the "products" index.
 * Using constants avoids typos in query code ("prcie" would silently match nothing).
 */
public final class ProductFields {

    public static final String SKU = "sku";
    public static final String NAME = "name";
    /** Keyword sub-field of name, used for alphabetical sorting. */
    public static final String NAME_KEYWORD = "name.keyword";
    public static final String DESCRIPTION = "description";
    public static final String BRAND = "brand";
    public static final String CATEGORY = "category";
    public static final String PRICE = "price";
    public static final String RAM_GB = "ramGb";
    public static final String RATING = "rating";
    public static final String TAGS = "tags";
    public static final String CREATED_AT = "createdAt";

    private ProductFields() {
    }
}
