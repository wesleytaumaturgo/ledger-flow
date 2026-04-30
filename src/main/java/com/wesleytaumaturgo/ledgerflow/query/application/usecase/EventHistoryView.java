package com.wesleytaumaturgo.ledgerflow.query.application.usecase;

import java.time.Instant;
import java.util.UUID;

/**
 * Read model view for a single event in the event history.
 *
 * eventData and eventMetadata are raw JSONB strings as stored in event_store.
 * Serialized to the HTTP response via @JsonRawValue in EventHistoryResponse.EventEntry,
 * so they appear as JSON objects (not escaped strings) in the API response.
 */
public record EventHistoryView(
    UUID eventId,
    String eventType,
    String eventData,
    String eventMetadata,
    long sequenceNumber,
    Instant occurredAt
) {}
