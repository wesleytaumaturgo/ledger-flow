package com.wesleytaumaturgo.ledgerflow.query.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable write model for persisting a transaction history entry.
 * Passed from projector to TransactionHistoryRepository.save().
 * No framework imports — pure Java record.
 */
public record TransactionHistoryData(
        UUID accountId,
        String eventType,
        BigDecimal amount,
        String currency,
        String description,
        Instant occurredAt,
        UUID counterpartyAccountId,
        long sequenceNumber
) {}
