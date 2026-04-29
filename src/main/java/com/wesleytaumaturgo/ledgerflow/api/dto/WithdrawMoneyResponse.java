package com.wesleytaumaturgo.ledgerflow.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawMoneyResponse(UUID accountId, BigDecimal balance, String currency) {}
