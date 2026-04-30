package com.wesleytaumaturgo.ledgerflow.api.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * HTTP response body for GET /api/v1/admin/accounts/{id}/events.
 *
 * EventEntry.eventData and EventEntry.eventMetadata are annotated with @JsonRawValue
 * so Jackson serializes them as JSON objects in the response (not as escaped strings).
 * The raw JSONB strings from event_store are passed through without re-encoding.
 *
 * Example response fragment:
 *   "eventData": {"ownerId": "user-123", "currency": "BRL"}
 *   NOT: "eventData": "{\"ownerId\": \"user-123\", \"currency\": \"BRL\"}"
 */
public record EventHistoryResponse(
    UUID accountId,
    List<EventEntry> events
) {
    public record EventEntry(
        UUID eventId,
        String eventType,
        @JsonRawValue String eventData,
        @JsonRawValue String eventMetadata,
        long sequenceNumber,
        Instant occurredAt
    ) {}
}
