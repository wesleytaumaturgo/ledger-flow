# Domain Glossary — LedgerFlow

> Ubiquitous Language — terms used consistently across code, docs, and conversations.

## Core Domain Terms

| Term | Definition |
|------|-----------|
| Account | Aggregate root. Has no persisted state table. Current state is rebuilt entirely from its event history via `reconstitute(events)`. |
| AccountId | Value Object wrapping a UUID. Identifies an Account uniquely. Never a raw UUID in domain code. |
| Money | Value Object wrapping a BigDecimal with currency. Scale is always 2, rounding is HALF_EVEN. Never a primitive double. |
| AccountStatus | Enum with values ACTIVE and INACTIVE. Inactive accounts cannot participate in transfers. |

## Event Sourcing Terms

| Term | Definition |
|------|-----------|
| Domain Event | Immutable fact about something that happened. Named in past tense: `AccountCreated`, `MoneyDeposited`. Carries `occurredAt` and `sequenceNumber`. |
| Event Store | Append-only PostgreSQL table `event_store`. Single source of truth. UPDATE and DELETE are forbidden. |
| Aggregate Replay | Reconstituting Account state by replaying all events for a given `aggregate_id` in `sequence_number ASC` order. |
| Sequence Number | Monotonically increasing integer per aggregate. Set by the aggregate before calling `apply()`. A gap or duplicate indicates a concurrent write conflict. |
| Optimistic Lock | Concurrency control via `UNIQUE(aggregate_id, sequence_number)`. Concurrent writes to the same aggregate produce a `DuplicateKeyException` which maps to `OptimisticLockException`. |
| occurredAt | Timestamp set at business operation time in the business method. Never set inside `apply()` — that would break determinism. |
| Uncommitted Events | Buffer inside `Account` holding events produced in the current operation, not yet persisted. Cleared atomically by `pullDomainEvents()`. |
| apply | Pure state mutation method inside `Account`. Zero business validation. Deterministic: same events always produce the same state. |

## CQRS Terms

| Term | Definition |
|------|-----------|
| Command Side | Write path. Use cases validate, produce events, and call `PostgresEventStore.save()`. Never reads from read model tables. |
| Query Side | Read path. Use cases read from denormalized tables only. Never touches `event_store` directly. |
| Projector | Component that listens to domain events via `@EventListener` and updates read model tables idempotently. Never throws. |
| Read Model | Denormalized projection table updated by a Projector. Optimized for query patterns, not write patterns. |
| AccountSummary | Read model for current account state: `currentBalance`, `totalDeposited`, `totalWithdrawn`, `transactionCount`, `lastEventSequence`. |
| TransactionHistory | Read model for paginated transaction history. Each row is one financial event with amount, type, and timestamp. |
| last_event_sequence | Column on every read model row tracking the last processed event. Used for idempotency: events with sequence number already processed are skipped. |
| Eventual Consistency | Read models are updated after the command transaction commits. Sub-millisecond lag for in-process events. Acceptable for a financial ledger with explicit rebuild capability. |

## Infrastructure Terms

| Term | Definition |
|------|-----------|
| Rebuild | Admin operation that truncates a read model and replays all events from Event Store to reconstruct it from scratch. Idempotent. |
| CorrelationIdFilter | Servlet filter that injects a UUID `traceId` into MDC for every request. Cleared in `finally` to prevent leaking between Virtual Thread re-uses. |
| AdminAuthFilter | Servlet filter that validates `X-Admin-Key` header against `ADMIN_API_KEY` env var for admin endpoints. |
| ProjectorFailedEventTracker | In-memory registry of events that failed projection. Used for diagnostics; does not retry automatically. |
| GlobalExceptionHandler | `@RestControllerAdvice` that maps domain and infrastructure exceptions to RFC 7807 `ProblemDetail` responses. No stack trace in the response body. |

## Business Rules Reference

| Rule | Description |
|------|-------------|
| RN-001 | Balance never goes negative — withdrawal above balance throws `InsufficientFundsException` |
| RN-002 | Deposit amount must be positive — zero or negative throws `InvalidAmountException` |
| RN-003 | Withdrawal amount must be positive — zero or negative throws `InvalidAmountException` |
| RN-004 | Transfer only allowed between active accounts |
| RN-005 | Inactive account cannot send or receive transfers |
| RN-006 | Self-transfer is forbidden — source and target account must differ |
| RN-007 | Optimistic lock conflict triggers retry up to `ledger.command.max-retries` times |
| RN-008 | Replay is deterministic — same events in same order always produce identical aggregate state |
