package com.wesleytaumaturgo.ledgerflow.command.application.usecase;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferMoneyResult(
    UUID sourceAccountId,
    UUID targetAccountId,
    BigDecimal amount,
    String currency
) {}
