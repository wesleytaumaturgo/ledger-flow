package com.wesleytaumaturgo.ledgerflow.command.application.usecase;

import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawMoneyResult(UUID accountId, BigDecimal balance, String currency) {}
