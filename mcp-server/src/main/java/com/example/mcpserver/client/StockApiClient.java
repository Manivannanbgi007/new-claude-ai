package com.example.mcpserver.client;

import com.example.mcpserver.dto.api.ApiAvailability;

import java.util.Optional;

/** Talks to stock-service through the API Gateway. */
public interface StockApiClient {

    /** @return empty if stock-service has no stock record for this SKU */
    Optional<ApiAvailability> checkAvailability(String sku, int quantity);
}
