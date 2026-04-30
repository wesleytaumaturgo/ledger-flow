package com.wesleytaumaturgo.ledgerflow.query.application.usecase;

import java.time.Instant;
import java.util.UUID;

public record EventHistoryView(
    UUID eventId,
    String eventType,
    long sequenceNumber,
    Instant occurredAt
) {}
