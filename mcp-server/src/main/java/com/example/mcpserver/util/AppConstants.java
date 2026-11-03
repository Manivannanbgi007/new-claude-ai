package com.example.mcpserver.util;

public final class AppConstants {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";
    /** Identifies the customer an order call is made for (logged by the gateway). */
    public static final String USER_ID_HEADER = "X-User-Id";
    /** order-service returns the existing order for a repeated key (no duplicate orders). */
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    /** Page size the chatbot gets when it does not ask for one. */
    public static final int DEFAULT_PAGE_SIZE = 10;
    /** Kept small on purpose: every product returned costs LLM context (tokens). */
    public static final int MAX_PAGE_SIZE = 20;

    private AppConstants() {
    }
}
