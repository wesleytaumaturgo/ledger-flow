package com.wesleytaumaturgo.ledgerflow.shared.infrastructure;

import com.wesleytaumaturgo.ledgerflow.command.domain.exception.DomainException;

/**
 * Thrown when an admin request is missing or supplies an invalid X-Admin-Key header.
 *
 * Error code: ADMIN_AUTH_REQUIRED (stable — part of API contract)
 * HTTP status: 401 Unauthorized
 *
 * This exception is raised by AdminAuthFilter to signal that the request
 * cannot proceed due to failed authentication. It is NOT raised by the
 * AdminController itself — the filter intercepts before the controller is reached.
 *
 * Logged at WARN (expected failure, not production incident) by GlobalExceptionHandler.
 */
public class AdminAuthException extends DomainException {

    public AdminAuthException() {
        super("Authentication required — X-Admin-Key header missing or invalid");
    }

    @Override
    public String errorCode() {
        return "ADMIN_AUTH_REQUIRED";
    }

    @Override
    public int httpStatus() {
        return 401;
    }
}
