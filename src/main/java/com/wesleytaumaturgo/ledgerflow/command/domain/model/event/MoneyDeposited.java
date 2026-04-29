package com.wesleytaumaturgo.ledgerflow.command.domain.model.event;

import com.wesleytaumaturgo.ledgerflow.command.domain.model.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Fact: a positive monetary amount was deposited at occurredAt.
 * Balance derivation: previous balance + amount.
 *
 * Persisted as event_type = "MoneyDeposited".
 */
public record MoneyDeposited(
    UUID eventId,
    UUID accountId,
    BigDecimal amount,
    String currency,
    Instant occurredAt,
    long sequenceNumber
) implements DomainEvent {

    public MoneyDeposited {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
