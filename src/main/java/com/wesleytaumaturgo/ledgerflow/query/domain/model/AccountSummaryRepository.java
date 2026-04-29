package com.wesleytaumaturgo.ledgerflow.query.domain.model;

import com.wesleytaumaturgo.ledgerflow.query.application.usecase.BalanceView;

import java.util.Optional;
import java.util.UUID;

/**
 * Domain repository interface for account summary read model.
 * Pure Java — zero Spring or JPA imports.
 * Implementation lives in query/infrastructure/persistence/.
 */
public interface AccountSummaryRepository {

    /**
     * Finds the balance view for the given account.
     *
     * @param accountId the account identifier
     * @return the balance view, or empty if the account has no summary yet
     */
    Optional<BalanceView> findById(UUID accountId);

    /**
     * Persists or updates the account summary state.
     * Called by the projector after processing domain events.
     *
     * @param data the account summary data to persist
     */
    void save(AccountSummaryData data);

    /**
     * Removes the account summary for the given account.
     * Used during projector replay — truncate then rebuild.
     *
     * @param accountId the account identifier
     */
    void deleteById(UUID accountId);
}
