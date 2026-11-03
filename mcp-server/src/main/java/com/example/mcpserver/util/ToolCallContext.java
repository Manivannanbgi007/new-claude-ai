package com.example.mcpserver.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Runs one tool call with its own correlation id and logs start, end and duration.
 * The id is put in the logging MDC, and CorrelationIdInterceptor copies it into the
 * X-Correlation-Id header, so gateway and product-service logs show the SAME id.
 */
public final class ToolCallContext {

    private static final Logger log = LoggerFactory.getLogger(ToolCallContext.class);

    private ToolCallContext() {
    }

    public static <T> T run(String toolName, Supplier<T> action) {
        MDC.put(AppConstants.CORRELATION_ID_MDC_KEY, UUID.randomUUID().toString());
        long start = System.nanoTime();
        try {
            T result = action.get();
            log.info("Tool {} succeeded in {} ms", toolName, elapsedMs(start));
            return result;
        } catch (RuntimeException ex) {
            log.warn("Tool {} failed in {} ms: {}", toolName, elapsedMs(start), ex.getMessage());
            throw ex;
        } finally {
            MDC.remove(AppConstants.CORRELATION_ID_MDC_KEY);
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
