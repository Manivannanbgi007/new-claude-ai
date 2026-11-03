package com.example.product.util;

public final class AppConstants {

    /** HTTP header carrying the id that links log lines of one request across services. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    /** Key used in the logging MDC (see logging.pattern.level in application.yml). */
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /** Elasticsearch default limit: from + size must not exceed 10,000. */
    public static final int MAX_RESULT_WINDOW = 10_000;

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private AppConstants() {
    }
}
