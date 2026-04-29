package com.wesleytaumaturgo.ledgerflow.command.domain.exception;

import com.wesleytaumaturgo.ledgerflow.command.domain.model.Money;

/**
 * Thrown when a withdrawal or transfer-debit would drive the balance negative.
 * Validation happens BEFORE the event is generated (REQ-reject-overdraft / FR-005).
 *
 * Error code: INSUFFICIENT_FUNDS (stable per STATE.md §Error Codes table)
 * HTTP status: 422 Unprocessable Entity
 */
public class InsufficientFundsException extends DomainException {

    public InsufficientFundsException(Money requested, Money available) {
        super(String.format(
            "Insufficient funds: requested %s, available %s",
            requested, available));
    }

    @Override
    public String errorCode() {
        return "INSUFFICIENT_FUNDS";
    }

    @Override
    public int httpStatus() {
        return 422;
    }
}
