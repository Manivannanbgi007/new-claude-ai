package com.example.stock.dto;

import com.example.stock.util.AppConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Check several products at once, e.g. a shopping cart (POST /api/v1/stock/availability). */
public record AvailabilityCheckRequest(

        @NotEmpty(message = "items must contain at least one line")
        @Size(max = AppConstants.MAX_LINES_PER_REQUEST, message = "at most 50 items per request")
        List<@Valid StockLineRequest> items) {
}
