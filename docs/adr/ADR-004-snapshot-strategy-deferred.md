# ADR-004 — Snapshot strategy deferred to post-MVP; threshold set at 500 events

* Status: Accepted
* Date: 2026-04-29

## Context and Problem Statement

Event-sourced aggregates rebuild state by replaying all stored events on every load. For the `Account`
aggregate, each command triggers a full replay of the account's event history before the business
operation executes. As the number of events per account grows, replay latency grows linearly.

A snapshot is a point-in-time serialization of the aggregate state stored alongside the event log.
On load, the system restores the snapshot and replays only events that occurred after the snapshot was
taken — bounding worst-case replay time to `O(events_since_snapshot)` instead of `O(total_events)`.

The question is: should snapshots be implemented now, as part of the MVP, or deferred?

## Decision Drivers

* MVP accounts are expected to have far fewer than 500 events (< 100 for realistic usage in the
  portfolio demo period).
* Snapshot infrastructure adds non-trivial complexity: a snapshot table, a snapshot serialization
  format, a snapshot write trigger, and a modified `load()` path in `PostgresEventStore`.
* The aggregate rule (`event-sourcing-aggregate.md`) explicitly calls out >1000 events as the
  threshold where snapshots become necessary.
* Premature optimization at MVP stage risks introducing a buggy snapshot path before the event schema
  is stable enough to snapshot reliably.

## Considered Options

* **No snapshots, ever** — accept O(n) replay; add pagination or archiving if needed.
* **Automatic snapshot on threshold** — `PostgresEventStore` writes a snapshot whenever
  `sequence_number` crosses a configured multiple (e.g., every 500 events).
* **Deferred snapshot (this decision)** — implement nothing now; document the threshold and design
  so it can be added later without breaking existing event streams.

## Decision Outcome

**Chosen option: Deferred snapshot — implement post-MVP when any account crosses 500 events.**

The `aggregate_snapshots` table schema is defined in the migration alongside `event_store` (DDL
exists, application code does not use it yet). `PostgresEventStore.loadSnapshot()` and
`saveSnapshot()` are declared in the `EventStore` interface but return `Optional.empty()` in the
current implementation, making the load path forward-compatible.

When implemented, the snapshot threshold will be 500 events. The automatic trigger will be placed
inside `PostgresEventStore.save()` — after persisting events, if the new sequence number crosses the
threshold, a snapshot is written within the same transaction.

### Positive Consequences

* MVP scope is not expanded by snapshot infrastructure that will not be exercised during the
  portfolio demo period.
* The `EventStore` interface already declares `loadSnapshot()` and `saveSnapshot()` — adding
  implementations later requires no interface changes and no use-case modifications.
* The threshold (500 events) is documented and agreed upon — the future implementer does not need
  to rediscover this decision.

### Negative Consequences

* Any account that somehow accumulates > 500 events before the snapshot feature is implemented will
  experience degraded replay performance. Acceptable for MVP scope; `ConcurrencyTest` and
  `ReplayDeterminismTest` catch regressions before they reach that scale.
* The `aggregate_snapshots` table exists in the schema but is unused — reviewers might flag it as
  dead DDL. A comment in the migration explains the intent.

## Pros and Cons of the Options

### No snapshots, ever

* Good, because simplest possible implementation — no snapshot code to test or maintain.
* Bad, because replay latency grows unbounded as accounts age — unacceptable in production.
* Bad, because denies the portfolio an opportunity to demonstrate snapshot awareness.

### Automatic snapshot on threshold (immediate)

* Good, because the performance guarantee is implemented from day one.
* Bad, because snapshot serialization format must be stable before the event schema is fully settled
  — changes to the `Account` aggregate state fields require snapshot migration logic.
* Bad, because increases test surface at MVP: snapshot write path, snapshot load path, and snapshot
  + tail replay correctness all need integration tests.

### Deferred snapshot (chosen)

* Good, because MVP ships faster with less risk.
* Good, because the interface contract is already declared — deferral is a controlled decision, not
  an oversight.
* Good, because snapshot format is defined after the event schema stabilizes, reducing migration risk.
* Bad, because replay performance is not bounded until the feature is implemented.
