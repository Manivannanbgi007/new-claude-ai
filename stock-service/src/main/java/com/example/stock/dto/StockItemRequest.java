package com.example.stock.dto;

import com.example.stock.util.AppConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Create a stock record for a product (POST /api/v1/stock). */
public record StockItemRequest(

        @NotBlank(message = "sku is required")
        @Pattern(regexp = AppConstants.SKU_REGEX, message = "sku may contain only letters, digits and '-' (max 50)")
        String sku,

        @NotNull(message = "quantityOnHand is required")
        @Min(value = 0, message = "quantityOnHand must not be negative")
        @Max(value = 1_000_000, message = "quantityOnHand must be at most 1,000,000")
        Integer quantityOnHand,

        @NotNull(message = "reorderLevel is required")
        @Min(value = 0, message = "reorderLevel must not be negative")
        @Max(value = 1_000_000, message = "reorderLevel must be at most 1,000,000")
        Integer reorderLevel) {
}
