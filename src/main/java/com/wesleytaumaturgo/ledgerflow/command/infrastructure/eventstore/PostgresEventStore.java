package com.wesleytaumaturgo.ledgerflow.command.infrastructure.eventstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wesleytaumaturgo.ledgerflow.command.domain.exception.OptimisticLockException;
import com.wesleytaumaturgo.ledgerflow.command.domain.model.DomainEvent;
import com.wesleytaumaturgo.ledgerflow.command.domain.repository.EventStoreRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * PostgreSQL implementation of EventStoreRepository.
 * Uses JdbcTemplate for append-only INSERT operations (no JPA — event store is not an entity).
 *
 * Concurrency: DuplicateKeyException on (aggregate_id, sequence_number) UNIQUE constraint
 * is caught and rethrown as OptimisticLockException (DEC-008).
 *
 * Transaction boundary: This class does NOT declare @Transactional. It participates in
 * the calling use case's @Transactional boundary (CLAUDE.md §7.1). The publish-after-persistence
 * invariant is preserved: publisher.publishEvent() is called after all INSERTs, within the
 * same outer transaction owned by the use case's execute() method (DEC-003, NFR-008).
 *
 * Append-only contract: This class contains NO UPDATE or DELETE statements.
 */
@Repository
public class PostgresEventStore implements EventStoreRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgresEventStore.class);

    private static final String INSERT_EVENT = """
            INSERT INTO event_store
              (id, aggregate_id, aggregate_type, event_type, event_data,
               event_metadata, sequence_number, occurred_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
            """;

    private static final String LOAD_EVENTS = """
            SELECT event_type, event_data, event_metadata, sequence_number, occurred_at
            FROM event_store
            WHERE aggregate_id = ?
            ORDER BY sequence_number ASC
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher publisher;
    private final EventDeserializer eventDeserializer;
    private final MeterRegistry meterRegistry;

    public PostgresEventStore(JdbcTemplate jdbc,
                               ObjectMapper objectMapper,
                               ApplicationEventPublisher publisher,
                               EventDeserializer eventDeserializer,
                               MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.publisher = publisher;
        this.eventDeserializer = eventDeserializer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void save(UUID aggregateId, String aggregateType, List<DomainEvent> events) {
        if (events.isEmpty()) {
            log.warn("save() called with empty event list for aggregate {}", aggregateId);
            return;
        }

        try {
            for (DomainEvent event : events) {
                jdbc.update(INSERT_EVENT,
                    UUID.randomUUID(),
                    aggregateId,
                    aggregateType,
                    event.getClass().getSimpleName(),
                    serialize(event),
                    "{}",
                    event.sequenceNumber(),
                    Timestamp.from(event.occurredAt()));
            }
        } catch (DuplicateKeyException e) {
            // UNIQUE(aggregate_id, sequence_number) violated — concurrent write detected
            log.warn("Optimistic lock conflict on aggregate {}: {}", aggregateId, e.getMessage());
            throw new OptimisticLockException(aggregateId);
        }

        // Increment after all INSERTs succeed, before event publication.
        // Tags with aggregate_type for metric cardinality (NFR-015 / WARNING-2 resolved).
        meterRegistry.counter("events.stored.total", "aggregate_type", aggregateType)
                     .increment(events.size());

        // Publish events AFTER all INSERTs succeed.
        // Transaction boundary is owned by the calling use case (@Transactional on execute()).
        // If the outer transaction rolls back, these Spring events are NOT published because
        // ApplicationEventPublisher dispatches synchronously within the same thread.
        events.forEach(publisher::publishEvent);

        log.debug("Saved {} event(s) for aggregate {}", events.size(), aggregateId);
    }

    @Override
    public List<DomainEvent> load(UUID aggregateId) {
        List<DomainEvent> events = jdbc.query(LOAD_EVENTS,
            (rs, rowNum) -> eventDeserializer.deserialize(
                rs.getString("event_type"),
                rs.getString("event_data"),
                rs.getLong("sequence_number"),
                rs.getTimestamp("occurred_at").toInstant()
            ),
            aggregateId);

        log.debug("Loaded {} event(s) for aggregate {}", events.size(), aggregateId);
        return events;
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new EventDeserializationException(
                "Failed to serialize event " + obj.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }
}
