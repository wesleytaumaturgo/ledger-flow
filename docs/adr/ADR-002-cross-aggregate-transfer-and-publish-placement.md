# ADR-002 — Cross-aggregate transfer in a single transaction and publishEvent placement in PostgresEventStore

* Status: Accepted
* Date: 2026-04-29

## Context and Problem Statement

`TransferMoneyUseCase` must debit the source account and credit the target account atomically. Both are
separate aggregate roots. Two questions arose together because the answers are coupled:

1. **Transaction boundary:** should the two aggregate writes be in the same database transaction or
   coordinated via a Saga across two separate transactions?
2. **Event publish placement:** should `ApplicationEventPublisher.publishEvent()` be called from the
   use case after `repository.save()`, or from inside `PostgresEventStore.save()` immediately after
   the INSERT?

## Decision Drivers

* Money must not be lost: a partial failure (debit succeeds, credit fails) must be impossible.
* Projectors receive events in the same order as they were written. Publishing after a committed
  transaction but before the next one is fragile — another thread can interleave.
* Portfolio scope does not require the infrastructure investment of a Saga framework.
* Events must be published only after their persistence is committed — projector state must never
  be ahead of the event store.

## Considered Options

**Transaction boundary:**

* **Single transaction** — both `save(source)` and `save(target)` run inside the same Spring
  `@Transactional` boundary in `TransferMoneyUseCase`.
* **Saga (two-phase, compensating transactions)** — separate transactions coordinated by an orchestrator;
  compensation event reverses the debit if the credit fails.

**Publish placement:**

* **Inside `PostgresEventStore.save()`** — after all INSERTs succeed, call `publisher.publishEvent()`
  for each event before the method returns (still within the same transaction).
* **In the use case, after `repository.save()`** — the use case calls `publisher.publishEvent()` on
  the events returned by `pullDomainEvents()`.

## Decision Outcome

**Chosen options:**

* **Single transaction** for the transfer: `TransferMoneyUseCase` wraps both saves in one
  `@Transactional` method. A database-level rollback undoes both writes atomically.
* **Publish inside `PostgresEventStore.save()`**: after all INSERTs are written within the active
  transaction, `publisher.publishEvent()` is called for each event. Spring's
  `ApplicationEventPublisher` dispatches synchronously inside the same transaction by default, so
  projectors run — and their writes commit — as part of the same unit of work.

### Positive Consequences

* Transfer is atomic by construction: partial failure is impossible — the database rolls back both
  aggregate writes together.
* Event publish ordering is guaranteed: source events are always inserted and published before target
  events, because `PostgresEventStore.save()` is called twice in sequence within the same transaction.
* Projectors receive each event exactly once in insertion order. No interleaving from concurrent
  requests is possible within the same transaction.
* Publish-after-persist invariant is satisfied: `publishEvent()` is called only after the INSERTs
  have been executed (though not yet committed — the projector writes also join the same transaction,
  so all-or-nothing applies).

### Negative Consequences

* Violates strict aggregate boundary purity: canonical Event Sourcing says cross-aggregate
  coordination must go through domain events and eventual consistency, not a shared transaction.
  This is an accepted trade-off for portfolio simplicity.
* `PostgresEventStore` depends on `ApplicationEventPublisher` — the infrastructure layer has a
  coupling to the in-process event bus. Documented in `in-process-events.md`.
* If the project ever moves to a distributed event bus (Kafka), the publish call must be extracted
  from `PostgresEventStore` and replaced with an outbox pattern.

## Pros and Cons of the Options

### Single transaction (chosen)

* Good, because atomicity is guaranteed by the database — no compensating logic required.
* Good, because failure modes are simple: either both writes commit or neither does.
* Bad, because it crosses two aggregate boundaries in one transaction — violates strict DDD
  aggregate isolation.

### Saga with compensating transactions

* Good, because each aggregate write is isolated in its own transaction — pure aggregate boundaries.
* Good, because scales to distributed systems where the two accounts could live in different services.
* Bad, because requires an orchestrator, a compensation event (`TransferReversed`), and additional
  test coverage for the rollback path — disproportionate complexity for a single-module portfolio.

### publishEvent in use case (rejected)

* Good, because the use case controls when events are published — feels natural.
* Bad, because between `save(source)` and `save(target)`, if publishing happened after the first
  save, projectors could observe a partially committed transfer.
* Bad, because event ordering across two `save()` calls depends on call order in the use case, not
  on insertion order in the store.
