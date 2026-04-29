package com.wesleytaumaturgo.ledgerflow.shared.infrastructure;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable snapshot of a projector failure.
 * Stored in ProjectorFailedEventTracker for observability and retry.
 */
public record FailedEventRecord(
        UUID eventId,
        String eventType,
        String accountId,
        Instant failedAt,
        String message
) {}
