package com.wesleytaumaturgo.ledgerflow.api.dto;

import com.wesleytaumaturgo.ledgerflow.query.application.usecase.BalanceView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * HTTP response DTO for GET /api/v1/accounts/{id}/balance.
 *
 * Maps from BalanceView (query use case output) to the API contract shape.
 * Exposes only the fields required by the API contract — does not expose
 * internal read-model fields (lastEventSequence, totalDeposited, totalWithdrawn).
 */
public record BalanceResponse(
        UUID accountId,
        BigDecimal balance,
        String currency,
        int transactionCount,
        Instant lastTransactionAt
) {

    /**
     * Maps a BalanceView from the query use case to this response record.
     */
    public static BalanceResponse from(BalanceView view) {
        return new BalanceResponse(
            view.accountId(),
            view.balance(),
            view.currency(),
            view.transactionCount(),
            view.lastTransactionAt()
        );
    }
}
