-- V003__create_account_summary.sql
-- Read model: denormalized account summary for the query side (CQRS).
-- No FK to event_store — rebuild (DELETE + replay) must work without FK violation.
-- Monetary columns use DECIMAL(19,2): exact arithmetic, no floating-point rounding.
-- last_event_sequence tracks idempotency for the AccountProjector.

CREATE TABLE account_summary (
    account_id          UUID            PRIMARY KEY,
    owner_id            VARCHAR(255)    NOT NULL,
    current_balance     DECIMAL(19, 2)  NOT NULL DEFAULT 0,
    currency            CHAR(3)         NOT NULL DEFAULT 'BRL',
    total_deposited     DECIMAL(19, 2)  NOT NULL DEFAULT 0,
    total_withdrawn     DECIMAL(19, 2)  NOT NULL DEFAULT 0,
    transaction_count   INT             NOT NULL DEFAULT 0,
    last_event_sequence BIGINT          NOT NULL DEFAULT 0,
    last_transaction_at TIMESTAMPTZ
);
