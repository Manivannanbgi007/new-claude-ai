package com.example.user.exception;

/** The client changed an old version: someone else updated the user since it was read. */
public class StaleVersionException extends RuntimeException {

    public StaleVersionException(String id, long expected, long actual) {
        super("User '" + id + "' is at version " + actual + ", but the change was based on version "
                + expected + ". Reload the user and apply your change again.");
    }
}
