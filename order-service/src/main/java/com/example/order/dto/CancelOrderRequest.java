package com.example.order.dto;

import jakarta.validation.constraints.Size;

/** POST /api/v1/orders/{id}/cancel. The comment is optional and only stored in the history. */
public record CancelOrderRequest(
        @Size(max = 300, message = "comment must be at most 300 characters")
        String comment) {
}
