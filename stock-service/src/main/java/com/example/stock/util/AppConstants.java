package com.example.stock.util;

public final class AppConstants {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /** Same SKU format as product-service. */
    public static final String SKU_REGEX = "^[A-Za-z0-9-]{1,50}$";
    /** Order ids come from the future order-service; letters, digits and '-' only. */
    public static final String ORDER_ID_REGEX = "^[A-Za-z0-9-]{1,64}$";

    public static final int MAX_QUANTITY_PER_LINE = 1_000;
    public static final int MAX_LINES_PER_REQUEST = 50;

    private AppConstants() {
    }
}
