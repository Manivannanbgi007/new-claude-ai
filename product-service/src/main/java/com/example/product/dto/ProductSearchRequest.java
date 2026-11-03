package com.example.product.dto;

import com.example.product.model.ProductSortField;
import com.example.product.model.SortDirection;
import com.example.product.util.AppConstants;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Query parameters of GET /api/v1/products. Every parameter is optional.
 * Example: /api/v1/products?q=gaming&category=laptop&maxPrice=1000&sortBy=price&page=0&size=10
 */
public record ProductSearchRequest(

        /** Free text, searched in name, brand, tags and description. */
        @Size(max = 200, message = "q must be at most 200 characters")
        String q,

        @Size(max = 100) String brand,

        @Size(max = 100) String category,

        @DecimalMin(value = "0.0", message = "minPrice must not be negative")
        BigDecimal minPrice,

        @DecimalMin(value = "0.0", message = "maxPrice must not be negative")
        BigDecimal maxPrice,

        @Min(value = 0, message = "minRamGb must not be negative")
        Integer minRamGb,

        @Min(value = 0, message = "maxRamGb must not be negative")
        Integer maxRamGb,

        @DecimalMin(value = "0.0", message = "minRating must be between 0 and 5")
        @DecimalMax(value = "5.0", message = "minRating must be between 0 and 5")
        Double minRating,

        @Size(max = 30) String tag,

        ProductSortField sortBy,

        SortDirection sortDirection,

        @Min(value = 0, message = "page must be 0 or greater")
        Integer page,

        @Min(value = 1, message = "size must be at least 1")
        @Max(value = AppConstants.MAX_PAGE_SIZE, message = "size must be at most 100")
        Integer size) {

    /** Compact constructor: fills in defaults for missing paging parameters. */
    public ProductSearchRequest {
        if (page == null) {
            page = AppConstants.DEFAULT_PAGE;
        }
        if (size == null) {
            size = AppConstants.DEFAULT_PAGE_SIZE;
        }
    }
}
