package com.example.order.dto;

import com.example.order.util.AppConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST /api/v1/orders.
 * userId is sent by the client for now; in the security phase it will come from the JWT.
 */
public record CreateOrderRequest(

        @NotBlank(message = "userId is required")
        @Pattern(regexp = AppConstants.UUID_REGEX, message = "userId must be a UUID")
        String userId,

        @NotEmpty(message = "items must contain at least one product")
        @Size(max = AppConstants.MAX_LINES_PER_ORDER, message = "an order can have at most 50 lines")
        List<@Valid OrderItemRequest> items) {
}
