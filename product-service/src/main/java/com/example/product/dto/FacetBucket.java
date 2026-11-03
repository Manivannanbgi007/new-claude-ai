package com.example.product.dto;

/** One facet value and how many products have it, e.g. ("laptop", 13). */
public record FacetBucket(String value, long count) {
}
