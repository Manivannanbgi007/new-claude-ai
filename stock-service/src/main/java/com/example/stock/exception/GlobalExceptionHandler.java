package com.example.stock.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
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

/** RFC 9457 ProblemDetail responses - the same format as the other services. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------------- business errors ----------------

    @ExceptionHandler(StockItemNotFoundException.class)
    public ProblemDetail handleStockNotFound(StockItemNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Stock item not found", ex.getMessage());
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ProblemDetail handleReservationNotFound(ReservationNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Reservation not found", ex.getMessage());
    }

    @ExceptionHandler(DuplicateStockItemException.class)
    public ProblemDetail handleDuplicate(DuplicateStockItemException ex) {
        return problem(HttpStatus.CONFLICT, "Duplicate stock item", ex.getMessage());
    }

    /** 409 + a "shortages" list, so the caller knows exactly which items are short. */
    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(InsufficientStockException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Insufficient stock", ex.getMessage());
        problem.setProperty("shortages", ex.getShortages());
        return problem;
    }

    @ExceptionHandler(InvalidStockOperationException.class)
    public ProblemDetail handleInvalidOperation(InvalidStockOperationException ex) {
        return problem(HttpStatus.CONFLICT, "Operation not allowed", ex.getMessage());
    }

    // ---------------- concurrency and database errors ----------------

    /** @Version check failed: someone else changed the row after we read it. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "The stock item was changed by another request. Reload it and try again.");
    }

    /** Lock wait timeout or deadlock detected by MySQL; retrying is safe. */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail handlePessimisticLock(PessimisticLockingFailureException ex) {
        log.warn("Database lock conflict: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, "Stock busy",
                "The stock is being updated by another request. Please retry.");
    }

    /** A database constraint rejected the change (unique key, check constraint, foreign key). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Data conflict",
                "The request conflicts with existing data. Reload and try again.");
    }

    /** MySQL down, or no free connection in the pool within 5 seconds. */
    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    public ProblemDetail handleDatabaseUnavailable(Exception ex) {
        log.error("Database unavailable", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Database unavailable",
                "The stock database is temporarily unavailable. Please try again later.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred.");
    }

    // ---------------- validation errors ----------------

    /** @Valid request bodies: lists every invalid field. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    /** Constraints on @PathVariable / @RequestParam, e.g. quantity=0 or an invalid sku. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result ->
                result.getResolvableErrors().forEach(error ->
                        errors.putIfAbsent(result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())));
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    private static ProblemDetail validationProblem(Map<String, String> errors) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid.");
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
