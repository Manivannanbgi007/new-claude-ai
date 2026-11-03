package com.example.mcpserver.client;

import com.example.mcpserver.dto.api.ApiFacets;
import com.example.mcpserver.dto.api.ApiPage;
import com.example.mcpserver.dto.api.ApiProduct;

import java.util.Map;

/**
 * Talks to the product API. The rest of the application depends on this
 * interface, not on HTTP details (Dependency Inversion).
 */
public interface ProductApiClient {

    /** @param queryParams already cleaned parameters; null values are not sent */
    ApiPage<ApiProduct> search(Map<String, Object> queryParams);

    ApiProduct getById(String id);

    ApiFacets getFacets();
}
