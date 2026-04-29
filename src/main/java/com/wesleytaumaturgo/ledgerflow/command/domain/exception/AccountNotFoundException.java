package com.wesleytaumaturgo.ledgerflow.command.domain.exception;

import java.util.UUID;

/**
 * Thrown when a use case attempts to load events for an unknown aggregate.
 * Detected as eventStore.load(aggregateId) returning an empty list.
 *
 * Extends NotFoundException to inherit httpStatus() = 404.
 * Error code: ACCOUNT_NOT_FOUND (stable per STATE.md §Error Codes table)
 */
public class AccountNotFoundException extends NotFoundException {

    public AccountNotFoundException(UUID accountId) {
        super("Account not found: " + accountId);
    }

    @Override
    public String errorCode() {
        return "ACCOUNT_NOT_FOUND";
    }
    // httpStatus() = 404 inherited from NotFoundException
}
