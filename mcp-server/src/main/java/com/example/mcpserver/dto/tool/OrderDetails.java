package com.example.mcpserver.dto.tool;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** An order as the LLM sees it. status: PENDING, CONFIRMED, REJECTED or CANCELLED. */
public record OrderDetails(
        String orderId,
        String status,
        BigDecimal totalUsd,
        List<OrderLineDetails> items,
        String failureReason,
        Instant createdAt) {
}
