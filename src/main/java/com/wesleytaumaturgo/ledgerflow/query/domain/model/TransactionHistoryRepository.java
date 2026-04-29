package com.wesleytaumaturgo.ledgerflow.query.domain.model;

import com.wesleytaumaturgo.ledgerflow.query.application.usecase.TransactionFilter;
import com.wesleytaumaturgo.ledgerflow.query.application.usecase.TransactionHistoryView;

import java.util.List;
import java.util.UUID;

/**
 * Domain repository interface for transaction history read model.
 * Pure Java — zero Spring or JPA imports.
 * Implementation lives in query/infrastructure/persistence/.
 *
 * Pagination parameters (offset, limit) are passed as primitives to avoid
 * importing Spring Pageable in the domain layer. The infrastructure adapter
 * maps these to Spring Data's Pageable.
 */
public interface TransactionHistoryRepository {

    /**
     * Returns a page of transaction history for the given account, applying optional filters.
     *
     * @param accountId the account to query
     * @param filter    optional filters (eventType, from, to) — null values mean no filter
     * @param page      zero-based page number
     * @param size      page size (max 50)
     * @return list of transaction views for the requested page
     */
    List<TransactionHistoryView> findByAccountId(
            UUID accountId,
            TransactionFilter filter,
            int page,
            int size);

    /**
     * Returns the total count of transactions for the given account and filter.
     * Used by the query use case to build pagination metadata.
     */
    long countByAccountId(UUID accountId, TransactionFilter filter);

    /**
     * Persists a transaction history entry.
     * Called by the projector after processing domain events.
     *
     * @param data the transaction history data to persist
     */
    void save(TransactionHistoryData data);

    /**
     * Removes all transaction history entries for the given account.
     * Used during projector replay — truncate then rebuild.
     *
     * @param accountId the account identifier
     */
    void deleteByAccountId(UUID accountId);
}
