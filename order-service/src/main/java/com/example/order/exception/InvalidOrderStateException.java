package com.example.order.exception;

/** The request is valid, but not allowed in the order's current status (e.g. cancel a rejected order). */
public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(String message) {
        super(message);
    }
}
