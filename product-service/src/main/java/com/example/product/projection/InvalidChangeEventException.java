package com.example.product.projection;

/** A change event that can never be applied (e.g. missing row). Goes straight to the DLT, no retries. */
public class InvalidChangeEventException extends RuntimeException {

    public InvalidChangeEventException(String message) {
        super(message);
    }
}
