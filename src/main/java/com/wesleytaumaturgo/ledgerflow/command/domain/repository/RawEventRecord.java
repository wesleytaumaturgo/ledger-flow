package com.wesleytaumaturgo.ledgerflow.command.domain.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Raw event record returned by EventStoreRepository.rawLoad().
 *
 * Carries unparsed JSONB payload strings directly from event_store columns.
 * Used by admin read paths that need to expose raw event data without deserializing
 * into domain event objects.
 *
 * eventData and eventMetadata are raw JSONB strings as stored in PostgreSQL.
 * Pure Java — no Spring, no JPA, no framework annotations.
 */
public record RawEventRecord(
    UUID eventId,
    String eventType,
    String eventData,
    String eventMetadata,
    long sequenceNumber,
    Instant occurredAt
) {}
