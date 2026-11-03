package com.example.product.service;

import com.example.product.dto.FacetResponse;
import com.example.product.dto.PageResponse;
import com.example.product.dto.ProductResponse;
import com.example.product.dto.ProductSearchRequest;

/** Read-only queries on the read model. All writes live in product-write-service. */
public interface ProductSearchService {

    PageResponse<ProductResponse> search(ProductSearchRequest request);

    FacetResponse getFacets();

    ProductResponse getById(String id);
}
