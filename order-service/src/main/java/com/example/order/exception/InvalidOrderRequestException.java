package com.example.order.exception;

/** The order request is well-formed but cannot be accepted (e.g. products in different currencies). */
public class InvalidOrderRequestException extends RuntimeException {

    public InvalidOrderRequestException(String message) {
        super(message);
    }
}
