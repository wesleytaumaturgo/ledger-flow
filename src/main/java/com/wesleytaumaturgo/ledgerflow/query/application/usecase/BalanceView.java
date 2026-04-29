package com.wesleytaumaturgo.ledgerflow.query.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable read-model DTO returned by balance queries and used by the projector.
 * Represents denormalized account state from the account_summary read model.
 * No framework imports — pure Java record.
 *
 * Fields ownerId, lastEventSequence, totalDeposited, totalWithdrawn are included
 * so the AccountProjector can perform idempotency checks and compute incremental
 * state updates from the existing read-model state.
 */
public record BalanceView(
        UUID accountId,
        String ownerId,
        BigDecimal balance,
        String currency,
        int transactionCount,
        long lastEventSequence,
        BigDecimal totalDeposited,
        BigDecimal totalWithdrawn,
        Instant lastTransactionAt
) {}
