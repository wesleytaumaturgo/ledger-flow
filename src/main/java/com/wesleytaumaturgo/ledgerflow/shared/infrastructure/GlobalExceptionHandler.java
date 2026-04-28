package com.wesleytaumaturgo.ledgerflow.shared.infrastructure;

import com.wesleytaumaturgo.ledgerflow.command.domain.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.stream.Collectors;

/**
 * Sole exception handler for all HTTP error responses.
 * Returns RFC 7807 ProblemDetail for all exceptions.
 *
 * Rules enforced:
 * - DomainException subtypes: WARN level, no stack trace in response
 * - Infrastructure/unexpected exceptions: ERROR level (stack trace to logs only)
 * - No stack trace EVER included in HTTP response body
 * - @ExceptionHandler MUST NOT be placed in any @RestController
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles all DomainException subtypes (business rule violations).
     * Logged at WARN — these are expected failures, not production incidents.
     * errorCode and httpStatus come from the exception itself — no switch required.
     * DomainException.httpStatus() returns int to keep domain layer Spring-free;
     * HttpStatus.resolve() maps the int to Spring's HttpStatus enum.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomain(DomainException ex) {
        log.warn("Domain rule violated [{}]: {}", ex.errorCode(), ex.getMessage());
        // No stack trace — expected failure; operators monitor errorCode patterns
        HttpStatus status = HttpStatus.resolve(ex.httpStatus());
        if (status == null) {
            status = HttpStatus.UNPROCESSABLE_ENTITY;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(ex.errorCode());
        problem.setProperty("errorCode", ex.errorCode());
        return ResponseEntity.status(status).body(problem);
    }

    /**
     * Handles @Valid constraint violations on @RequestBody parameters.
     * Returns 400 Bad Request with field-level detail.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", detail);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), detail);
        problem.setTitle("VALIDATION_ERROR");
        problem.setProperty("errorCode", "VALIDATION_ERROR");
        return ResponseEntity.status(ex.getStatusCode()).body(problem);
    }

    /**
     * Catches all unexpected exceptions (infrastructure failures, bugs).
     * Logged at ERROR with stack trace (production incident level).
     * Response contains NO implementation details — generic message only.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, WebRequest request) {
        log.error("Unexpected error on {}", request.getDescription(false), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again or contact support.");
        problem.setTitle("INTERNAL_ERROR");
        problem.setProperty("errorCode", "INTERNAL_ERROR");
        return ResponseEntity.internalServerError().body(problem);
    }
}
