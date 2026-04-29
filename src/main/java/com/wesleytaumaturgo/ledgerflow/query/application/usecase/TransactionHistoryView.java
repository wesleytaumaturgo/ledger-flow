package com.wesleytaumaturgo.ledgerflow.query.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable read-model DTO returned by transaction history queries.
 * Represents one row from the transaction_history read model.
 * counterpartyAccountId is null for non-transfer events.
 * No framework imports — pure Java record.
 */
public record TransactionHistoryView(
        UUID id,
        UUID accountId,
        String eventType,
        BigDecimal amount,
        String currency,
        String description,
        Instant occurredAt,
        UUID counterpartyAccountId
) {}
