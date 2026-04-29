package com.wesleytaumaturgo.ledgerflow.command.domain.model.event;

/**
 * Direction of a transfer event from the perspective of one aggregate.
 * DEBIT  — money leaves this account (source)
 * CREDIT — money arrives at this account (target)
 *
 * Used as a field on TransferCompleted events so a single event type covers both
 * sides of a transfer (one DEBIT row + one CREDIT row in event_store per transfer).
 */
public enum TransferDirection {
    DEBIT,
    CREDIT
}
