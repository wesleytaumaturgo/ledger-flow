package com.wesleytaumaturgo.ledgerflow.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventHistoryResponse(
    UUID accountId,
    List<EventEntry> events
) {
    public record EventEntry(
        UUID eventId,
        String eventType,
        long sequenceNumber,
        Instant occurredAt
    ) {}
}
