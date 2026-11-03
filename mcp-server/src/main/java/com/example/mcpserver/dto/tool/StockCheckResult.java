package com.example.mcpserver.dto.tool;

/**
 * Result of checkProductStock. status: IN_STOCK, LOW_STOCK, OUT_OF_STOCK or NOT_STOCKED.
 * canBeOrdered = true only if at least requestedQuantity units are available right now.
 */
public record StockCheckResult(
        String sku,
        int requestedQuantity,
        int availableQuantity,
        String status,
        boolean canBeOrdered,
        String message) {
}
