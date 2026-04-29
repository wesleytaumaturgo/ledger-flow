package com.wesleytaumaturgo.ledgerflow.command.application.usecase;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record WithdrawMoneyCommand(UUID accountId, BigDecimal amount, String currency) {
    public WithdrawMoneyCommand {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
    }
}
