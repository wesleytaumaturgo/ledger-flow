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
     *
     * Native SQL rationale: PostgreSQL cannot infer the data type of a null JPQL bind parameter
     * (e.g., Instant → TIMESTAMPTZ) when the parameter is null and appears only in an IS NULL
     * check. Using native SQL with explicit CAST resolves the type-ambiguity error at the driver
     * level. This is a known Hibernate 6 + PostgreSQL limitation for nullable typed parameters.
     */
    @Query(value = """
            SELECT * FROM transaction_history t
            WHERE t.account_id = :accountId
              AND (CAST(:eventType AS TEXT) IS NULL OR t.event_type = :eventType)
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR t.occurred_at >= CAST(:from AS TIMESTAMPTZ))
              AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR t.occurred_at <= CAST(:to AS TIMESTAMPTZ))
            ORDER BY t.occurred_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM transaction_history t
            WHERE t.account_id = :accountId
              AND (CAST(:eventType AS TEXT) IS NULL OR t.event_type = :eventType)
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR t.occurred_at >= CAST(:from AS TIMESTAMPTZ))
              AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR t.occurred_at <= CAST(:to AS TIMESTAMPTZ))
            """,
            nativeQuery = true)
    Page<TransactionHistoryEntity> findByAccountIdWithFilters(
            @Param("accountId") UUID accountId,
            @Param("eventType") String eventType,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    /**
     * Returns the total count matching the optional filters.
     * Mirrors findByAccountIdWithFilters — same WHERE clause for consistent pagination metadata.
     *
     * Native SQL rationale: same PostgreSQL null-type inference limitation as above.
     */
    @Query(value = """
            SELECT COUNT(*) FROM transaction_history t
            WHERE t.account_id = :accountId
              AND (CAST(:eventType AS TEXT) IS NULL OR t.event_type = :eventType)
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR t.occurred_at >= CAST(:from AS TIMESTAMPTZ))
              AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR t.occurred_at <= CAST(:to AS TIMESTAMPTZ))
            """,
            nativeQuery = true)
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
