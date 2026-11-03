package com.example.rag.exception;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.web.client.ResourceAccessException;
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

    @ExceptionHandler(DocumentNotFoundException.class)
    public ProblemDetail handleNotFound(DocumentNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Document not found", ex.getMessage());
    }

    @ExceptionHandler(DuplicateDocumentException.class)
    public ProblemDetail handleDuplicate(DuplicateDocumentException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Duplicate document", ex.getMessage());
        problem.setProperty("existingDocumentId", ex.getExistingDocumentId());
        return problem;
    }

    @ExceptionHandler(InvalidDocumentException.class)
    public ProblemDetail handleInvalidDocument(InvalidDocumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid document", ex.getMessage());
    }

    /** The ingestion queue is full: too many uploads at once. */
    @ExceptionHandler(TaskRejectedException.class)
    public ProblemDetail handleQueueFull(TaskRejectedException ex) {
        log.warn("Ingestion queue full: {}", ex.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Too many uploads",
                "Too many documents are being processed. Please upload again in a few minutes.");
    }

    /** Ollama (embeddings / chat) or the reranker is not reachable. */
    @ExceptionHandler({ResourceAccessException.class, TransientAiException.class})
    public ProblemDetail handleAiUnavailable(Exception ex) {
        log.error("AI model not reachable", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "AI model unavailable",
                "The AI model is not reachable right now. Please try again later.");
    }

    @ExceptionHandler(NonTransientAiException.class)
    public ProblemDetail handleAiError(NonTransientAiException ex) {
        log.error("AI model request failed", ex);
        return problem(HttpStatus.BAD_GATEWAY, "AI model error",
                "The AI model rejected the request. Check that the chat and embedding models are pulled in Ollama.");
    }

    /** Two updates of the same version raced; the database noticed via @Version. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "The document was changed by another request. Reload it and try again.");
    }

    /** Duplicate upload raced (unique sha256), or a constraint rejected a value. */
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
                "The knowledge base database is temporarily unavailable. Please try again later.");
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
