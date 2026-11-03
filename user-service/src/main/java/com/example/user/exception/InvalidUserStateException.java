package com.example.user.exception;

/** The request is valid, but not allowed in the user's current state (e.g. updating a deleted user). */
public class InvalidUserStateException extends RuntimeException {

    public InvalidUserStateException(String message) {
        super(message);
    }
}
