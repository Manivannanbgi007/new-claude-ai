package com.example.stock.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Replace the counted stock and reorder level (PUT /api/v1/stock/{sku}),
 * e.g. after a physical stock count. For "+10 received" use an adjustment instead.
 */
public record StockItemUpdateRequest(

        @NotNull(message = "quantityOnHand is required")
        @Min(value = 0, message = "quantityOnHand must not be negative")
        @Max(value = 1_000_000, message = "quantityOnHand must be at most 1,000,000")
        Integer quantityOnHand,

        @NotNull(message = "reorderLevel is required")
        @Min(value = 0, message = "reorderLevel must not be negative")
        @Max(value = 1_000_000, message = "reorderLevel must be at most 1,000,000")
        Integer reorderLevel) {
}
