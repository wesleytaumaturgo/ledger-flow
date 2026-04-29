package com.wesleytaumaturgo.ledgerflow.query.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable write model for persisting account summary state.
 * Passed from projector to AccountSummaryRepository.save().
 * No framework imports — pure Java record.
 */
public record AccountSummaryData(
        UUID accountId,
        String ownerId,
        BigDecimal currentBalance,
        String currency,
        BigDecimal totalDeposited,
        BigDecimal totalWithdrawn,
        int transactionCount,
        long lastEventSequence,
        Instant lastTransactionAt
) {}
