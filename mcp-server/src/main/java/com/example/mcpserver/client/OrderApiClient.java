package com.example.mcpserver.client;

import com.example.mcpserver.dto.api.ApiCreateOrderRequest;
import com.example.mcpserver.dto.api.ApiOrder;
import com.example.mcpserver.dto.api.ApiPage;

/** Talks to order-service through the API Gateway. */
public interface OrderApiClient {

    /** @param idempotencyKey the same key returns the same order instead of creating a second one */
    ApiOrder create(ApiCreateOrderRequest request, String idempotencyKey);

    ApiOrder getById(String orderId);

    ApiPage<ApiOrder> listForUser(String userId, String status, int page, int size);
}
