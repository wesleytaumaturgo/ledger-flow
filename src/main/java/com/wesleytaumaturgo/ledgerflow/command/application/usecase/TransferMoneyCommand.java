package com.wesleytaumaturgo.ledgerflow.command.application.usecase;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record TransferMoneyCommand(
    UUID sourceAccountId,
    UUID targetAccountId,
    BigDecimal amount,
    String currency
) {
    public TransferMoneyCommand {
        Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
        Objects.requireNonNull(targetAccountId, "targetAccountId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
    }
}
