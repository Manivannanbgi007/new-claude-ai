package com.example.stock.dto;

import com.example.stock.model.StockStatus;

/** available = true when availableQuantity >= requestedQuantity. */
public record AvailabilityResponse(
        String sku,
        int requestedQuantity,
        int availableQuantity,
        boolean available,
        StockStatus status) {
}
