package com.example.stock.dto;

import com.example.stock.model.ReservationStatus;

import java.time.Instant;
import java.util.List;

/** All lines of one order share the same status (operations are all-or-nothing). */
public record ReservationResponse(
        String orderId,
        ReservationStatus status,
        List<ReservationLineResponse> items,
        Instant createdAt,
        Instant updatedAt) {
}
