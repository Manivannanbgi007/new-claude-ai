package com.example.product.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions into RFC 9457 "problem detail" JSON responses.
 * Extending ResponseEntityExceptionHandler means Spring MVC's own errors
 * (malformed JSON, wrong HTTP method, ...) use the same format automatically.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleNotFound(ProductNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Product not found", ex.getMessage());
    }

    @ExceptionHandler(InvalidSearchRequestException.class)
    public ProblemDetail handleInvalidSearch(InvalidSearchRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid search request", ex.getMessage());
    }

    /** Spring Data throws this when Elasticsearch cannot be reached. */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ProblemDetail handleSearchEngineDown(DataAccessResourceFailureException ex) {
        log.error("Elasticsearch is unavailable", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Search engine unavailable",
                "The product search engine is temporarily unavailable. Please try again later.");
    }

    /** Last safety net: log everything, but never leak internal details to the client. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred.");
    }

    /**
     * Validation errors for @Valid request bodies AND @Valid query-parameter objects.
     * Returns every invalid field, e.g. "errors": {"price": "price is required"}.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            String message = error.isBindingFailure()
                    // Type conversion failed, e.g. maxPrice=abc or sortBy=colour
                    ? "Invalid value '" + error.getRejectedValue() + "'"
                    : error.getDefaultMessage();
            errors.putIfAbsent(error.getField(), message);
        }

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid.");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
