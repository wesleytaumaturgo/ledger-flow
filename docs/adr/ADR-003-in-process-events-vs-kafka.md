# ADR-003 — In-process Spring ApplicationEventPublisher vs Kafka for projector fan-out

* Status: Accepted
* Date: 2026-04-29

## Context and Problem Statement

After an event is persisted to the event store, projectors (`AccountProjector`) must be notified to
update read models (`account_summary`, `transaction_history`). Two main approaches exist: publish via
an in-process `ApplicationEventPublisher` that dispatches synchronously within the same JVM, or
publish to an external Kafka topic and have projectors consume asynchronously from a broker.

## Decision Drivers

* Read model staleness: how long can the query side lag behind the write side?
* Operational complexity: does the project benefit from the infrastructure investment?
* Failure semantics: what happens if a projector fails mid-update?
* Portfolio scope: the project runs as a single Spring Boot process with a single PostgreSQL instance.

## Considered Options

* **Spring `ApplicationEventPublisher` (in-process)** — events dispatched synchronously within the
  same transaction; projectors run in the same JVM thread.
* **Kafka** — events serialized and written to a Kafka topic; projectors consume asynchronously from
  a consumer group, potentially on separate nodes.
* **Outbox pattern + polling** — events written to an `outbox` table in the same transaction;
  a separate polling thread reads and dispatches them, decoupling commit from publish.

## Decision Outcome

**Chosen option: Spring `ApplicationEventPublisher` in-process.**

`PostgresEventStore.save()` calls `publisher.publishEvent()` for each persisted event. Spring
dispatches synchronously; projectors run within the same database transaction. Read model is updated
atomically with the event store — staleness is zero within a transaction.

See `in-process-events.md` for the rule governing this pattern.

### Positive Consequences

* Zero infrastructure overhead: no Kafka, no ZooKeeper, no Schema Registry — the project runs with
  a single `docker compose up -d` (PostgreSQL only).
* Read model is consistent with the event store within the same transaction — no staleness window
  for a single-node deployment.
* Failure handling is straightforward: if a projector throws, the transaction rolls back and the
  event store write is also rolled back — no orphaned events in the store with unprojected state.
  (Projectors are written not to throw — they catch and log — but the safety net is there.)
* Observable without Kafka tooling: projector lag visible through `last_event_sequence` column
  differential — queryable directly in PostgreSQL.

### Negative Consequences

* Does not demonstrate Kafka integration — a recruiter looking specifically for Kafka experience
  will not see it here. (StreamBridge project covers that pattern.)
* Synchronous dispatch means projector processing time adds to the command response time. For
  `AccountProjector`, this is negligible (single-row UPSERT), but would be a problem at scale.
* If the project ever needs to fan out to multiple services or process events across nodes, this
  approach requires replacement with an outbox pattern or a broker.

## Pros and Cons of the Options

### Spring ApplicationEventPublisher (chosen)

* Good, because no additional infrastructure — single `docker compose up -d` is sufficient.
* Good, because read model is updated atomically with the event store — no eventual consistency gap.
* Good, because rule `in-process-events.md` constrains projectors to be pure read-model updates,
  making synchronous dispatch safe.
* Bad, because projector latency is added to command latency synchronously.
* Bad, because does not scale beyond a single JVM without an outbox or broker.

### Kafka

* Good, because decouples event production from consumption — projectors can scale independently.
* Good, because demonstrates a production-grade pattern for distributed systems.
* Bad, because requires Kafka, ZooKeeper (or KRaft), and Schema Registry — significantly increases
  `docker-compose.yml` complexity and local setup friction.
* Bad, because introduces eventual consistency: read model lags by at least one Kafka poll interval
  (typically 100–500ms) — acceptable for analytics, problematic for balance reads immediately after
  a deposit.

### Outbox pattern + polling

* Good, because decouples publish from transaction commit — more resilient to application crashes
  than in-process publish.
* Good, because can be evolved into Kafka later (Debezium reads the outbox table).
* Bad, because adds an outbox table, a polling scheduler, and additional failure scenarios — more
  complexity than the portfolio scope warrants.
