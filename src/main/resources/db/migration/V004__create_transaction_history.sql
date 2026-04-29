-- V004__create_transaction_history.sql
-- Read model: transaction history for the query side (CQRS).
-- No FK to event_store or account_summary — rebuild (DELETE + replay) must work
-- without FK violation.
-- Monetary columns use DECIMAL(19,2): exact arithmetic, no floating-point rounding.
-- counterparty_account_id is nullable: only populated for transfer events.
-- Composite indexes optimised for the two primary query patterns:
--   1. Paginated history by account ordered by most recent first
--   2. Filtered history by account and event type

CREATE TABLE transaction_history (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id              UUID            NOT NULL,
    event_type              VARCHAR(50),
    amount                  DECIMAL(19, 2),
    currency                CHAR(3)         NOT NULL DEFAULT 'BRL',
    description             VARCHAR(500),
    occurred_at             TIMESTAMPTZ     NOT NULL,
    counterparty_account_id UUID,
    sequence_number         BIGINT          NOT NULL
);

-- Primary query pattern: paginated history per account, newest first
CREATE INDEX idx_transaction_history_account_occurred
    ON transaction_history (account_id, occurred_at DESC);

-- Secondary query pattern: filter by event type within an account
CREATE INDEX idx_transaction_history_account_type
    ON transaction_history (account_id, event_type);
