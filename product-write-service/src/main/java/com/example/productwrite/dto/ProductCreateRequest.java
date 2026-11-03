package com.example.productwrite.dto;

import com.example.productwrite.util.AppConstants;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** POST /api/v1/products */
public record ProductCreateRequest(

        @NotBlank(message = "sku is required")
        @Pattern(regexp = AppConstants.SKU_REGEX, message = "sku may contain only letters, digits and '-' (max 50)")
        String sku,

        @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        @NotBlank(message = "brand is required")
        @Size(max = 100, message = "brand must be at most 100 characters")
        String brand,

        @NotBlank(message = "category is required")
        @Size(max = 100, message = "category must be at most 100 characters")
        String category,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.01", message = "price must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "price must have at most 2 decimal places")
        BigDecimal price,

        @Min(value = 1, message = "ramGb must be at least 1")
        @Max(value = 1024, message = "ramGb must be at most 1024")
        Integer ramGb,

        @DecimalMin(value = "0.0", message = "rating must be between 0 and 5")
        @DecimalMax(value = "5.0", message = "rating must be between 0 and 5")
        Double rating,

        @Size(max = 20, message = "at most 20 tags are allowed")
        List<@Pattern(regexp = AppConstants.TAG_REGEX,
                message = "tags may contain only lower-case letters, digits and '-' (max 30)") String> tags) {
}
