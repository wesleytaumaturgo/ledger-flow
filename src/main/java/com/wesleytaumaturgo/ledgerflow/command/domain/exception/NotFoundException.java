package com.wesleytaumaturgo.ledgerflow.command.domain.exception;

/**
 * Abstract base for all "not found" domain exceptions.
 * Subtypes inherit HTTP 404 status; must still provide their own errorCode().
 */
public abstract class NotFoundException extends DomainException {

    protected NotFoundException(String message) {
        super(message);
    }

    @Override
    public int httpStatus() {
        return 404;
    }
}
