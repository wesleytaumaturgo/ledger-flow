package com.wesleytaumaturgo.ledgerflow.command.domain.exception;

import java.util.UUID;

/**
 * Thrown when a transfer involves an account whose status is not ACTIVE (D-02).
 * In MVP every account is always ACTIVE so the check always passes — but the
 * validation path is wired so the post-MVP status-change feature drops in
 * without touching the aggregate or the use case.
 *
 * Error code: ACCOUNT_INACTIVE (extends the stable code table for D-02)
 * HTTP status: 422 Unprocessable Entity
 */
public class AccountInactiveException extends DomainException {

    public AccountInactiveException(UUID accountId) {
        super("Account is not active: " + accountId);
    }

    @Override
    public String errorCode() {
        return "ACCOUNT_INACTIVE";
    }

    @Override
    public int httpStatus() {
        return 422;
    }
}
