package com.wesleytaumaturgo.ledgerflow.command.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker interface for all domain events.
 * Events are immutable facts in past tense (e.g. MoneyDeposited, AccountCreated).
 *
 * Contract:
 * - eventId: unique identifier for this specific event occurrence
 * - occurredAt: wall-clock time when the business operation executed (set at operation time, NOT in apply())
 * - sequenceNumber: position of this event within its aggregate's history (aggregate-scoped, not global)
 *
 * IMPORTANT: No @JsonTypeInfo or Jackson annotations here — domain layer must remain pure Java.
 * Serialization is the responsibility of PostgresEventStore via EventDeserializer (infrastructure).
 */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();

    long sequenceNumber();
}
