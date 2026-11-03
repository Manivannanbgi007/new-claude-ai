package com.example.product.filter;

import com.example.product.util.AppConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request a correlation id: reuses the X-Correlation-Id header sent by
 * the caller (the gateway, later) or creates a new one. The id appears in every
 * log line of the request and is returned in the response header, so one user
 * action can be traced across all services.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** Only accept safe values, so a caller cannot inject fake lines into our logs. */
    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(AppConstants.CORRELATION_ID_HEADER);
        if (correlationId == null || !VALID_ID.matcher(correlationId).matches()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(AppConstants.CORRELATION_ID_MDC_KEY, correlationId);
        response.setHeader(AppConstants.CORRELATION_ID_HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Threads are reused; always clean up so the id doesn't leak into the next request.
            MDC.remove(AppConstants.CORRELATION_ID_MDC_KEY);
        }
    }
}
