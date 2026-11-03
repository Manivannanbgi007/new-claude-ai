package com.example.stock.exception;

public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(String orderId) {
        super("No reservation exists for order '" + orderId + "'");
    }
}
