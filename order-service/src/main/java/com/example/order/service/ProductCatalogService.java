package com.example.order.service;

import com.example.events.product.ProductSnapshot;
import com.example.order.dto.CatalogProductResponse;
import com.example.order.dto.PageResponse;

/** Keeps and exposes order-service's local copy of the product catalogue. */
public interface ProductCatalogService {

    void apply(ProductSnapshot snapshot);

    void remove(String sku);

    PageResponse<CatalogProductResponse> list(int page, int size);

    CatalogProductResponse get(String sku);
}
