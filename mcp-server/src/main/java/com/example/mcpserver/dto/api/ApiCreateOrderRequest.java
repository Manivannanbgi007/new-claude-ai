package com.example.mcpserver.dto.api;

import java.util.List;

/** Body of order-service POST /api/v1/orders. Prices are NOT sent: order-service owns them. */
public record ApiCreateOrderRequest(String userId, List<Item> items) {

    public record Item(String sku, int quantity) {
    }
}
