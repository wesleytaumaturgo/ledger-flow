package com.wesleytaumaturgo.ledgerflow.command.domain.model.event;

import com.wesleytaumaturgo.ledgerflow.command.domain.model.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Fact: a transfer completed for one side of the operation.
 * Each transfer produces TWO TransferCompleted events in event_store —
 * one row with direction=DEBIT (source aggregate) and one with direction=CREDIT
 * (target aggregate). counterpartId points to the OTHER aggregate.
 *
 * Persisted as event_type = "TransferCompleted".
 */
public record TransferCompleted(
    UUID eventId,
    UUID accountId,
    UUID counterpartId,
    BigDecimal amount,
    String currency,
    TransferDirection direction,
    Instant occurredAt,
    long sequenceNumber
) implements DomainEvent {

    public TransferCompleted {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(counterpartId, "counterpartId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
