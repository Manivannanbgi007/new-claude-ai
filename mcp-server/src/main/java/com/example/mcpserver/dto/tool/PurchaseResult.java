package com.example.mcpserver.dto.tool;

import java.util.List;

/**
 * Result of purchaseProduct.
 *   outcome     ORDER_CONFIRMED  order placed and stock reserved
 *               ORDER_REJECTED   order placed, but stock-service rejected it (see order.failureReason)
 *               ORDER_PENDING    order placed, stock check still running - use getOrderDetails later
 *               NOT_PURCHASED    NO order was created (see reason)
 *   reason      for NOT_PURCHASED: PRODUCT_NOT_FOUND, AMBIGUOUS_PRODUCT, NOT_STOCKED, INSUFFICIENT_STOCK
 *   order       null when NOT_PURCHASED
 *   candidates  for AMBIGUOUS_PRODUCT: the matching product names - ask the user which one
 */
public record PurchaseResult(
        String outcome,
        String reason,
        String productName,
        String sku,
        int quantity,
        StockCheckResult stockCheck,
        OrderDetails order,
        List<String> candidates,
        String message) {
}
