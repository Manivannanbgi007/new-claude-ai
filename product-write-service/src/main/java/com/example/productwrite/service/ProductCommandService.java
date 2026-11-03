package com.example.productwrite.service;

import com.example.productwrite.dto.ProductCreateRequest;
import com.example.productwrite.dto.ProductResponse;
import com.example.productwrite.dto.ProductUpdateRequest;

/**
 * Commands that change products. "Command" = the CQRS name for a write.
 * Queries (search, filters, facets) belong to the read side, product-service.
 */
public interface ProductCommandService {

    ProductResponse create(ProductCreateRequest request);

    ProductResponse update(String id, ProductUpdateRequest request);

    void delete(String id);

    /** Reads the committed source-of-truth state (for admins and for verifying the projection). */
    ProductResponse getById(String id);
}
