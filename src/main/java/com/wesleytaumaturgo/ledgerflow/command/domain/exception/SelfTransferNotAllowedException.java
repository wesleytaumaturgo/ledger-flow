package com.wesleytaumaturgo.ledgerflow.command.domain.exception;

import java.util.UUID;

/**
 * Thrown when source and target account IDs are equal in a transfer command.
 * Validated FIRST in TransferMoneyUseCase.doExecute() — before any DB I/O —
 * so invalid requests do not waste eventStore.load() calls.
 *
 * Error code: SELF_TRANSFER_NOT_ALLOWED (stable per STATE.md §Error Codes table)
 * HTTP status: 422 Unprocessable Entity
 */
public class SelfTransferNotAllowedException extends DomainException {

    public SelfTransferNotAllowedException(UUID accountId) {
        super("Self-transfer not allowed for account: " + accountId);
    }

    @Override
    public String errorCode() {
        return "SELF_TRANSFER_NOT_ALLOWED";
    }

    @Override
    public int httpStatus() {
        return 422;
    }
}
