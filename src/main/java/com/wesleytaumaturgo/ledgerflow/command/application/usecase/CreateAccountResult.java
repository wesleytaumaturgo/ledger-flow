package com.wesleytaumaturgo.ledgerflow.command.application.usecase;

import java.util.UUID;

/**
 * Result of CreateAccountUseCase.execute. Returns the newly assigned account ID.
 * Per use-cases rule 4: NEVER return the aggregate.
 */
public record CreateAccountResult(UUID accountId) {}
