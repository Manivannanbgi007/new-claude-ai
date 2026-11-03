package com.example.mcpserver.dto.api;

/** Response of stock-service GET /api/v1/stock/{sku}/availability (through the gateway). */
public record ApiAvailability(
        String sku,
        int requestedQuantity,
        int availableQuantity,
        boolean available,
        String status) {
}
