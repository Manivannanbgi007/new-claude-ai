package com.example.stock.service;

import com.example.stock.dto.ReservationRequest;
import com.example.stock.dto.ReservationResponse;

import java.util.Optional;

/** Reservation life cycle for orders: reserve -> confirm, or reserve -> release. */
public interface ReservationService {

    /** Result of reserve(): created=false means this order was already reserved (idempotent retry). */
    record ReserveResult(ReservationResponse reservation, boolean created) {
    }

    ReserveResult reserve(ReservationRequest request);

    ReservationResponse get(String orderId);

    ReservationResponse confirm(String orderId);

    ReservationResponse release(String orderId);

    /**
     * Undo everything held for an order (saga compensation for OrderCancelled):
     *   RESERVED  -> units released (available again)
     *   CONFIRMED -> units put back on hand (the order was cancelled after confirmation)
     * @return the released reservation, or empty if there was nothing to undo
     *         (no reservation, or already RELEASED)
     */
    Optional<ReservationResponse> cancelForOrder(String orderId);
}
