package com.wesleytaumaturgo.ledgerflow.query.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable read-model DTO returned by balance queries.
 * Represents denormalized account state from the account_summary read model.
 * No framework imports — pure Java record.
 */
public record BalanceView(
        UUID accountId,
        BigDecimal balance,
        String currency,
        int transactionCount,
        Instant lastTransactionAt
) {}
