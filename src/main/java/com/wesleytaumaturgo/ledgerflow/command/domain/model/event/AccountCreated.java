package com.wesleytaumaturgo.ledgerflow.command.domain.model.event;

import com.wesleytaumaturgo.ledgerflow.command.domain.model.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Fact: an account was created at occurredAt with starting balance zero.
 * sequenceNumber for AccountCreated is always 1L (first event of an aggregate).
 *
 * Persisted as event_type = "AccountCreated" (DefaultEventDeserializer registry key).
 */
public record AccountCreated(
    UUID eventId,
    UUID accountId,
    String ownerId,
    Instant occurredAt,
    long sequenceNumber
) implements DomainEvent {

    public AccountCreated {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
