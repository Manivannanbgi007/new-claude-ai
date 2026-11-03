package com.example.mcpserver.exception;

/**
 * The product API returned an error or could not be reached.
 * The message is written for the LLM: when a tool throws, Spring AI sends the
 * message back to the model as the tool result, so it must be clear and actionable.
 */
public class ProductApiException extends RuntimeException {

    public ProductApiException(String message) {
        super(message);
    }

    public ProductApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
