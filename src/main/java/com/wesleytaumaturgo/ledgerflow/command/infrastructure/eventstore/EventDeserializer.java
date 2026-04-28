package com.wesleytaumaturgo.ledgerflow.command.infrastructure.eventstore;

import com.wesleytaumaturgo.ledgerflow.command.domain.model.DomainEvent;

import java.time.Instant;

/**
 * Strategy for deserializing stored event JSON back to DomainEvent instances.
 * Implementations hold a registry mapping event_type strings to concrete DomainEvent classes.
 *
 * Phase 2 registers concrete event types (AccountCreated, MoneyDeposited, etc.)
 * by calling registerEventType() on DefaultEventDeserializer during application startup.
 */
public interface EventDeserializer {

    /**
     * Deserialize a stored event row back to its DomainEvent subtype.
     *
     * @param eventType      value from the event_type column (e.g. "AccountCreated")
     * @param eventDataJson  raw JSONB string from event_data column
     * @param sequenceNumber from sequence_number column
     * @param occurredAt     from occurred_at column
     * @return deserialized DomainEvent; must not return null
     * @throws EventDeserializationException if eventType is unknown or JSON is malformed
     */
    DomainEvent deserialize(String eventType, String eventDataJson,
                            long sequenceNumber, Instant occurredAt);
}
