package com.example.order.dto;

import com.example.order.model.CancellationReason;
import com.example.order.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * An order. status PENDING = waiting for the stock check (usually well under a second);
 * poll GET /api/v1/orders/{id} to see CONFIRMED or REJECTED.
 */
public record OrderResponse(
        String id,
        String userId,
        OrderStatus status,
        String currency,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        String failureReason,
        CancellationReason cancellationReason,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
