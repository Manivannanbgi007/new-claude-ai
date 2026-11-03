package com.example.productwrite.exception;

/** The client updated an old version: someone else changed the product since it was read. */
public class StaleVersionException extends RuntimeException {

    public StaleVersionException(String id, long expected, long actual) {
        super("Product '" + id + "' is at version " + actual + ", but the update was based on version "
                + expected + ". Reload the product and apply your change again.");
    }
}
