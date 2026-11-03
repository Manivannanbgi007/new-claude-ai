package com.example.order.model;

/**
 * Order life cycle. The saga moves the order forward; the allowed transitions are
 * a business rule and live here.
 *
 *   PENDING --StockReserved---------> CONFIRMED --cancel--> CANCELLED
 *      |----StockReservationFailed--> REJECTED
 *      '----cancel------------------> CANCELLED
 *
 * Future phases add states between PENDING and CONFIRMED (AWAITING_PAYMENT) and
 * after CONFIRMED (SHIPPED, DELIVERED) without changing this pattern.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED;

    public boolean canChangeTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == REJECTED || target == CANCELLED;
            case CONFIRMED -> target == CANCELLED;
            case REJECTED, CANCELLED -> false;
        };
    }

    public boolean isFinal() {
        return this == REJECTED || this == CANCELLED;
    }
}
