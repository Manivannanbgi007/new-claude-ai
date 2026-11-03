package com.example.gateway.exception;

import org.springframework.http.HttpStatus;

import java.time.Duration;

/**
 * The gateway itself refused a request on purpose (e.g. bulkhead full) - not an error
 * of a backend. GatewayExceptionHandler turns it into a ProblemDetail with this title,
 * detail and a Retry-After header, so clients know WHEN to try again.
 */
public class GatewayRejectionException extends RuntimeException {

    private final HttpStatus status;
    private final String title;
    private final Duration retryAfter;

    public GatewayRejectionException(HttpStatus status, String title, String detail, Duration retryAfter) {
        super(detail);
        this.status = status;
        this.title = title;
        this.retryAfter = retryAfter;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
