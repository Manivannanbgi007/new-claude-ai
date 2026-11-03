package com.example.stock.dto;

import com.example.stock.util.AppConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Reserve stock for an order (POST /api/v1/stock/reservations) - called by order-service. */
public record ReservationRequest(

        @NotBlank(message = "orderId is required")
        @Pattern(regexp = AppConstants.ORDER_ID_REGEX, message = "orderId may contain only letters, digits and '-' (max 64)")
        String orderId,

        @NotEmpty(message = "items must contain at least one line")
        @Size(max = AppConstants.MAX_LINES_PER_REQUEST, message = "at most 50 items per order")
        List<@Valid StockLineRequest> items) {
}
