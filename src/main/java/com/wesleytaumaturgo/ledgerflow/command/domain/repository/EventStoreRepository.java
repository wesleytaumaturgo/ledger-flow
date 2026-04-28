package com.wesleytaumaturgo.ledgerflow.command.domain.repository;

import com.wesleytaumaturgo.ledgerflow.command.domain.model.DomainEvent;

import java.util.List;
import java.util.UUID;

/**
 * Domain port for the append-only event store.
 * Implemented in infrastructure by PostgresEventStore.
 *
 * No snapshot methods — deferred to post-MVP (D-01).
 * Repository is append-only: save() only appends. No update or delete operations exist.
 */
public interface EventStoreRepository {

    /**
     * Append events for an aggregate. Throws OptimisticLockException if
     * (aggregate_id, sequence_number) UNIQUE constraint is violated by concurrent write.
     *
     * @param aggregateId   the aggregate's identity
     * @param aggregateType the aggregate type name (e.g. "Account")
     * @param events        uncommitted events from the aggregate (must not be empty)
     */
    void save(UUID aggregateId, String aggregateType, List<DomainEvent> events);

    /**
     * Load all events for an aggregate ordered by sequence_number ASC.
     * Returns an empty list (not an exception) when no events exist for the given aggregateId.
     *
     * @param aggregateId the aggregate's identity
     * @return ordered immutable list of events; empty if aggregate does not exist
     */
    List<DomainEvent> load(UUID aggregateId);
}
