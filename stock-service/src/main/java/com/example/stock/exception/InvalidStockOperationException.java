package com.example.stock.exception;

/** The request is valid, but not allowed in the current state (e.g. release a confirmed reservation). */
public class InvalidStockOperationException extends RuntimeException {

    public InvalidStockOperationException(String message) {
        super(message);
    }
}
