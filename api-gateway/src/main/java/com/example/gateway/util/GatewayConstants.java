package com.example.gateway.util;

public final class GatewayConstants {

    /** Same header name as in product-service, so the id travels through all services. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /**
     * Identifies the user a request is made for. Today it is set by trusted callers
     * (e.g. mcp-server for chatbot orders); in the security phase the gateway will set
     * it from the validated JWT and remove any value a client sent itself.
     */
    public static final String USER_ID_HEADER = "X-User-Id";

    /** Key under which the correlation id is stored in the exchange attributes. */
    public static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    private GatewayConstants() {
    }
}
