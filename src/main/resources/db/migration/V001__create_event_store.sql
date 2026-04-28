-- V001__create_event_store.sql
-- Append-only event store for CQRS + Event Sourcing.
-- UPDATE and DELETE on this table are forbidden (enforced by ArchUnit in ArchitectureTest).
-- UNIQUE(aggregate_id, sequence_number) is the sole concurrency control mechanism (DEC-008).

CREATE TABLE event_store (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID        NOT NULL,
    aggregate_type  VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    event_data      JSONB       NOT NULL,
    event_metadata  JSONB       NOT NULL DEFAULT '{}'::jsonb,
    sequence_number BIGINT      NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_event_aggregate_sequence UNIQUE (aggregate_id, sequence_number)
);

-- Primary access pattern: load all events for an aggregate ordered by sequence
CREATE INDEX idx_event_store_aggregate
    ON event_store (aggregate_id, sequence_number);

-- Chronological queries for debugging and analytics
CREATE INDEX idx_event_store_occurred_at
    ON event_store (occurred_at);

-- Event type filter for audit queries (Phase 4)
CREATE INDEX idx_event_store_event_type
    ON event_store (event_type);
