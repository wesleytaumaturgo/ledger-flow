package com.wesleytaumaturgo.ledgerflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferMoneyRequest(
    @NotNull(message = "targetAccountId is required")
    UUID targetAccountId,

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    BigDecimal amount,

    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be ISO 4217 (3 characters)")
    String currency
) {}
