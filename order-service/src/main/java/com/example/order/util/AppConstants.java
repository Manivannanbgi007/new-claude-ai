package com.example.order.util;

public final class AppConstants {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /** Optional request header: the same key for the same user returns the same order (safe retries). */
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    public static final String UUID_REGEX = "^[0-9a-fA-F-]{36}$";
    public static final String SKU_REGEX = "^[A-Za-z0-9-]{1,50}$";
    public static final String SAFE_KEY_REGEX = "^[A-Za-z0-9-]{1,64}$";

    public static final int MAX_LINES_PER_ORDER = 50;
    public static final int MAX_QUANTITY_PER_LINE = 1_000;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private AppConstants() {
    }
}
