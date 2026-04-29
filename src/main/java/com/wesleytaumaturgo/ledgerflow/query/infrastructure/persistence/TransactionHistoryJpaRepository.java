package com.wesleytaumaturgo.ledgerflow.query.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data JPA repository for the transaction_history read model.
 * Supports optional filters for event type and date range without spurious WHERE clauses
 * when filter values are null (using the :param IS NULL OR t.field = :param pattern).
 */
interface TransactionHistoryJpaRepository extends JpaRepository<TransactionHistoryEntity, UUID> {

    /**
     * Returns paginated transaction history with optional filters.
     * Null filter values are treated as "no constraint" — full results for that dimension.
     */
    @Query("""
            SELECT t FROM TransactionHistoryEntity t
            WHERE t.accountId = :accountId
              AND (:eventType IS NULL OR t.eventType = :eventType)
              AND (:from IS NULL OR t.occurredAt >= :from)
              AND (:to IS NULL OR t.occurredAt <= :to)
            ORDER BY t.occurredAt DESC
            """)
    Page<TransactionHistoryEntity> findByAccountIdWithFilters(
            @Param("accountId") UUID accountId,
            @Param("eventType") String eventType,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    /**
     * Returns the total count matching the optional filters.
     * Mirrors findByAccountIdWithFilters — same WHERE clause for consistent pagination metadata.
     */
    @Query("""
            SELECT COUNT(t) FROM TransactionHistoryEntity t
            WHERE t.accountId = :accountId
              AND (:eventType IS NULL OR t.eventType = :eventType)
              AND (:from IS NULL OR t.occurredAt >= :from)
              AND (:to IS NULL OR t.occurredAt <= :to)
            """)
    long countByAccountIdWithFilters(
            @Param("accountId") UUID accountId,
            @Param("eventType") String eventType,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Deletes all transaction history entries for the given account.
     * Used by projector replay to truncate before rebuilding.
     */
    void deleteByAccountId(UUID accountId);
}
