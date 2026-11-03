package com.example.order.exception;

import org.apache.kafka.common.errors.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Same ProblemDetail format as every other service. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleNotFound(OrderNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Order not found", ex.getMessage());
    }

    @ExceptionHandler(UnknownProductException.class)
    public ProblemDetail handleUnknownProduct(UnknownProductException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Unknown product", ex.getMessage());
        problem.setProperty("unknownSkus", ex.getUnknownSkus());
        return problem;
    }

    @ExceptionHandler(InvalidOrderRequestException.class)
    public ProblemDetail handleInvalidRequest(InvalidOrderRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid order", ex.getMessage());
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ProblemDetail handleInvalidState(InvalidOrderStateException ex) {
        return problem(HttpStatus.CONFLICT, "Operation not allowed", ex.getMessage());
    }

    /** The outbox could not serialize the event: Schema Registry down or schemas not registered. */
    @ExceptionHandler(SerializationException.class)
    public ProblemDetail handleEventSerialization(SerializationException ex) {
        log.error("Event serialization failed (is Schema Registry running and are the schemas registered?)", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Event infrastructure unavailable",
                "The order could not be saved because events cannot be published right now. Please try again later.");
    }

    /** Two updates of the same version raced; the database noticed via @Version. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "The order was changed at the same time (for example by the stock check). Reload it and try again.");
    }

    /** Duplicate idempotency key raced, or a CHECK constraint rejected a value. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Data conflict",
                "The request conflicts with existing data. Reload and try again.");
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    public ProblemDetail handleDatabaseUnavailable(Exception ex) {
        log.error("Database unavailable", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Database unavailable",
                "The order database is temporarily unavailable. Please try again later.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "An unexpected error occurred.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result ->
                result.getResolvableErrors().forEach(error ->
                        errors.putIfAbsent(result.getMethodParameter().getParameterName(), error.getDefaultMessage())));
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    private static ProblemDetail validationProblem(Map<String, String> errors) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid.");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
