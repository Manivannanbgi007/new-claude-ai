package com.example.mcpserver.exception;

/** The LLM sent an argument we cannot use; the message tells it how to fix the call. */
public class InvalidToolArgumentException extends RuntimeException {

    public InvalidToolArgumentException(String message) {
        super(message);
    }
}
