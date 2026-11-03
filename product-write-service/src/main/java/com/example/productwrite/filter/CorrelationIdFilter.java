package com.example.productwrite.filter;

import com.example.productwrite.util.AppConstants;
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

/** Same behaviour as in the other services: one id per request, in logs and response header. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

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
            MDC.remove(AppConstants.CORRELATION_ID_MDC_KEY);
        }
    }
}
