package com.wesleytaumaturgo.ledgerflow.command.domain.exception;

/**
 * Thrown when a monetary amount is null, zero, or negative.
 * Raised by Money.of() (deposit/withdraw/transfer paths) BEFORE any event is produced.
 *
 * Error code: INVALID_AMOUNT (stable per STATE.md §Error Codes table)
 * HTTP status: 422 Unprocessable Entity
 */
public class InvalidAmountException extends DomainException {

    public InvalidAmountException(String detail) {
        super("Invalid amount: " + detail);
    }

    @Override
    public String errorCode() {
        return "INVALID_AMOUNT";
    }

    @Override
    public int httpStatus() {
        return 422;
    }
}
