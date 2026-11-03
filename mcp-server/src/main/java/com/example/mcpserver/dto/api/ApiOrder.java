package com.example.mcpserver.dto.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** An order as returned by order-service (through the gateway). */
public record ApiOrder(
        String id,
        String userId,
        String status,
        String currency,
        BigDecimal totalAmount,
        List<ApiOrderItem> items,
        String failureReason,
        String cancellationReason,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
