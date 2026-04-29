package com.wesleytaumaturgo.ledgerflow.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferMoneyResponse(
    UUID sourceAccountId,
    UUID targetAccountId,
    BigDecimal amount,
    String currency
) {}
