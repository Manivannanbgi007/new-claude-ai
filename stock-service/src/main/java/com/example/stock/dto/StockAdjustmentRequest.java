package com.example.stock.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Relative change (POST /api/v1/stock/{sku}/adjustments):
 * +20 = delivery received, -2 = damaged units written off.
 */
public record StockAdjustmentRequest(

        @NotNull(message = "quantityChange is required")
        @Min(value = -100_000, message = "quantityChange must be at least -100,000")
        @Max(value = 100_000, message = "quantityChange must be at most 100,000")
        Integer quantityChange,

        @NotBlank(message = "reason is required")
        @Size(max = 200, message = "reason must be at most 200 characters")
        String reason) {
}
