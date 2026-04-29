package com.wesleytaumaturgo.ledgerflow.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositMoneyResponse(UUID accountId, BigDecimal balance, String currency) {}
