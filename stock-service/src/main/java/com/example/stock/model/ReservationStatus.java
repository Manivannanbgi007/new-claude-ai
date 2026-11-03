package com.example.stock.model;

/**
 * Life cycle of a reservation:
 *   RESERVED  -> CONFIRMED  (order completed: units leave the warehouse)
 *   RESERVED  -> RELEASED   (order cancelled: units become available again)
 *   CONFIRMED -> RELEASED   (order cancelled after confirmation: units go back on hand;
 *                            only through the saga, see ReservationService.cancelForOrder)
 */
public enum ReservationStatus {
    RESERVED,
    CONFIRMED,
    RELEASED
}
