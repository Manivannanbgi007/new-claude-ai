package com.example.stock.dto;

import com.example.stock.model.StockStatus;

import java.time.Instant;

public record StockItemResponse(
        String sku,
        int quantityOnHand,
        int quantityReserved,
        int quantityAvailable,
        int reorderLevel,
        StockStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
