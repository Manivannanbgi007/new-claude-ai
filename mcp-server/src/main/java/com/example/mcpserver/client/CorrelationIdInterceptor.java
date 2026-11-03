package com.example.mcpserver.client;

import com.example.mcpserver.util.AppConstants;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.UUID;

/** Adds the current tool call's correlation id to every outgoing HTTP request. */
public class CorrelationIdInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        String correlationId = MDC.get(AppConstants.CORRELATION_ID_MDC_KEY);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        request.getHeaders().set(AppConstants.CORRELATION_ID_HEADER, correlationId);
        return execution.execute(request, body);
    }
}
