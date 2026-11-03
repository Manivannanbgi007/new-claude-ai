package com.example.mcpclient.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Same ProblemDetail error format as product-service and the gateway. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Ollama is not reachable (container stopped, wrong URL). */
    @ExceptionHandler(ResourceAccessException.class)
    public ProblemDetail handleUnreachable(ResourceAccessException ex) {
        log.error("AI model not reachable", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "AI assistant unavailable",
                "The AI assistant is not reachable right now. Please try again later.");
    }

    /** Temporary LLM problem that persisted after the retries. */
    @ExceptionHandler(TransientAiException.class)
    public ProblemDetail handleTransient(TransientAiException ex) {
        log.error("Temporary AI model error", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "AI assistant unavailable",
                "The AI assistant is temporarily unavailable. Please try again later.");
    }

    /** Permanent LLM error, e.g. model not downloaded or invalid request. */
    @ExceptionHandler(NonTransientAiException.class)
    public ProblemDetail handleNonTransient(NonTransientAiException ex) {
        log.error("AI model request failed", ex);
        return problem(HttpStatus.BAD_GATEWAY, "AI model error",
                "The AI model rejected the request. Check the model configuration.");
    }

    @ExceptionHandler(ChatProcessingException.class)
    public ProblemDetail handleChatProcessing(ChatProcessingException ex) {
        log.warn("Chat processing failed: {}", ex.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "No answer", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
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
