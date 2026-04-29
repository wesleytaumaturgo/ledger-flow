package com.wesleytaumaturgo.ledgerflow.api.dto;

import com.wesleytaumaturgo.ledgerflow.query.application.usecase.BalanceView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BalanceResponse(
        UUID accountId,
        BigDecimal balance,
        String currency,
        int transactionCount,
        BigDecimal totalDeposited,
        BigDecimal totalWithdrawn,
        Instant lastTransactionAt
) {

    public static BalanceResponse from(BalanceView view) {
        return new BalanceResponse(
            view.accountId(),
            view.balance(),
            view.currency(),
            view.transactionCount(),
            view.totalDeposited(),
            view.totalWithdrawn(),
            view.lastTransactionAt()
        );
    }
}
