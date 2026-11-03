package com.example.mcpclient.util;

public final class AppConstants {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /** Allowed format for conversation ids (same rule as correlation ids). */
    public static final String SAFE_ID_REGEX = "^[A-Za-z0-9-]{1,64}$";

    private AppConstants() {
    }
}
