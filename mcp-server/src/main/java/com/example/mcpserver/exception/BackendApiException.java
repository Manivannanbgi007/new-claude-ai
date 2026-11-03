package com.example.mcpserver.exception;

/**
 * A backend API (reached through the API Gateway) returned an error or was unreachable.
 * The message is written for the LLM: when a tool throws, Spring AI sends the message
 * back to the model as the tool result, so it must be clear and actionable.
 */
public class BackendApiException extends RuntimeException {

    /** HTTP status of the failed call (503 if the gateway could not be reached at all). */
    private final int status;

    public BackendApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public BackendApiException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public boolean isNotFound() {
        return status == 404;
    }
}
