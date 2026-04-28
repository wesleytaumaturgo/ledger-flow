package com.wesleytaumaturgo.ledgerflow.command.domain.exception;

/**
 * Abstract base for all domain exceptions.
 * Subtypes provide a stable errorCode and integer HTTP status code.
 * Mapped to RFC 7807 ProblemDetail by GlobalExceptionHandler (shared/infrastructure/).
 *
 * Rules:
 * - errorCode() MUST be SCREAMING_SNAKE_CASE and STABLE between releases
 * - httpStatus() returns a plain int — domain layer has NO Spring imports
 * - GlobalExceptionHandler resolves the int to Spring HttpStatus via HttpStatus.resolve()
 * - Domain exceptions are logged at WARN level (expected failures) — never ERROR
 * - Stack trace is NEVER included in HTTP responses
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    /**
     * Stable identifier for this exception type. Part of the API contract.
     * Format: SCREAMING_SNAKE_CASE. Example: INSUFFICIENT_FUNDS
     * MUST NOT change between releases — clients and runbooks depend on it.
     */
    public abstract String errorCode();

    /**
     * Raw HTTP status code returned to the caller when this exception is raised.
     * Returns a plain int to keep the domain layer free of Spring imports.
     * GlobalExceptionHandler maps this to HttpStatus via HttpStatus.resolve(ex.httpStatus()).
     */
    public abstract int httpStatus();
}
