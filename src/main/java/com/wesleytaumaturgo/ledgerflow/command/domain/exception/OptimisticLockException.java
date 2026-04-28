package com.wesleytaumaturgo.ledgerflow.command.domain.exception;

import java.util.UUID;

/**
 * Thrown when two concurrent writes attempt the same (aggregate_id, sequence_number) pair.
 * PostgresEventStore catches DuplicateKeyException from Spring JDBC and rethrows this.
 *
 * Error code: OPTIMISTIC_LOCK_CONFLICT (stable, per STATE.md §Error Codes)
 * HTTP status: 409 Conflict
 */
public class OptimisticLockException extends DomainException {

    public OptimisticLockException(UUID aggregateId) {
        super(String.format(
            "Concurrent modification detected on aggregate %s. Reload and retry.", aggregateId));
    }

    @Override
    public String errorCode() {
        return "OPTIMISTIC_LOCK_CONFLICT";
    }

    @Override
    public int httpStatus() {
        return 409;
    }
}
