package com.example.productwrite.service;

/** Publishes the product catalogue to other services (topic product.catalog.v1). */
public interface CatalogPublishService {

    /** Writes a ProductSnapshot for EVERY product to the outbox; returns how many. */
    int republishAll();
}
