package com.example.stock.dto;

import com.example.stock.util.AppConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** One product and quantity, used by availability checks and reservations. */
public record StockLineRequest(

        @NotBlank(message = "sku is required")
        @Pattern(regexp = AppConstants.SKU_REGEX, message = "sku may contain only letters, digits and '-' (max 50)")
        String sku,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        @Max(value = AppConstants.MAX_QUANTITY_PER_LINE, message = "quantity must be at most 1000")
        Integer quantity) {
}
