package com.example.mcpserver.client;

import com.example.mcpserver.exception.BackendApiException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

/**
 * Shared error handling for every call to the API Gateway (products, stock, orders).
 * Turns HTTP failures into BackendApiException with an LLM-readable message built from
 * the ProblemDetail the services return.
 */
public final class GatewayCalls {

    private GatewayCalls() {
    }

    /** @param apiName used in messages, e.g. "product catalogue", "stock service" */
    public static <T> T execute(String apiName, Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientResponseException ex) {
            throw new BackendApiException(ex.getStatusCode().value(), describe(apiName, ex), ex);
        } catch (ResourceAccessException ex) {
            // Connection refused or timeout: the gateway itself is not reachable.
            throw new BackendApiException(503,
                    "The " + apiName + " is currently unreachable. Tell the user to try again later.", ex);
        }
    }

    private static String describe(String apiName, RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        ProblemDetail problem = null;
        try {
            problem = ex.getResponseBodyAs(ProblemDetail.class);
        } catch (RuntimeException ignored) {
            // Body was not a ProblemDetail; fall back to the status code below.
        }

        StringBuilder message = new StringBuilder(capitalize(apiName)).append(" error ").append(status);
        if (problem != null && problem.getDetail() != null) {
            message.append(": ").append(problem.getDetail());
            Object errors = problem.getProperties() == null ? null : problem.getProperties().get("errors");
            if (errors != null) {
                message.append(" Invalid fields: ").append(errors);
            }
        }
        if (status >= 500) {
            message.append(" The ").append(apiName).append(" has a temporary problem. Tell the user to try again later.");
        }
        return message.toString();
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
