package com.example.order.dto;

import com.example.order.model.OrderStatus;

import java.time.Instant;

public record OrderStatusHistoryResponse(OrderStatus fromStatus, OrderStatus toStatus, String reason, Instant changedAt) {
}
