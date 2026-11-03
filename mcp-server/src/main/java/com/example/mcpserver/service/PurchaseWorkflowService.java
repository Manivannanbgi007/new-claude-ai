package com.example.mcpserver.service;

import com.example.mcpserver.dto.tool.PurchaseResult;

/**
 * The purchase workflow: Get Product -> Check Stock -> Create Order -> Return Result.
 * Called ONLY for an explicit purchase request (see PurchaseTools and mcp-client's
 * PurchaseIntentDetector).
 */
public interface PurchaseWorkflowService {

    /**
     * @param productName the product as the customer named it (always available in the chat)
     * @param productId   optional: an id from a searchProducts result of the SAME reply
     */
    PurchaseResult purchase(String productName, String productId, Integer quantity);
}
