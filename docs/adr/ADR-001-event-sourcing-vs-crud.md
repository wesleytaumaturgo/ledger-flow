# ADR-001 — Event Sourcing as primary persistence vs CRUD with audit log

* Status: Accepted
* Date: 2026-04-29

## Context and Problem Statement

LedgerFlow is a financial ledger. Every financial operation — deposit, withdrawal, transfer — must be
auditable: who did what, when, and in what sequence. The question is how to persist account state.

Two viable approaches exist: store the current state as a mutable row and append an audit log alongside
it (CRUD + audit), or store only the sequence of events that led to the current state and derive it on
demand (Event Sourcing).

## Decision Drivers

* Audit trail must be the authoritative record, not a derived log.
* Account state at any past point in time must be reconstructable deterministically.
* The portfolio's stated purpose is to demonstrate Event Sourcing patterns — the persistence model
  must reflect that.
* Concurrent write conflicts must be detectable at the persistence layer without application-level locking.

## Considered Options

* **Event Sourcing** — append-only `event_store` table; state rebuilt via `Account.reconstitute(events)`.
* **CRUD + audit table** — `accounts` row holds current state; a separate `account_audit_log` table
  records every change.
* **CRUD only** — single `accounts` table, no audit trail.

## Decision Outcome

**Chosen option: Event Sourcing.**

The event store is the single source of truth. There is no `accounts` state table. Current balance and
transaction history are derived read models built by `AccountProjector` — they can be discarded and
rebuilt from the event log at any time.

### Positive Consequences

* Audit trail is the record itself, not a derivative. No code path can update state without producing
  an auditable event.
* Point-in-time reconstruction is deterministic: replaying the same event list always yields the same
  state (`ReplayDeterminismTest` verifies this on every build).
* Optimistic concurrency control is enforced at the database level via
  `UNIQUE(aggregate_id, sequence_number)` — no application-level locks required.
* Read models are disposable. A projector bug can be fixed and read models rebuilt without data loss.

### Negative Consequences

* Current state requires replay or a maintained read model — direct SQL queries on `accounts` are not
  possible.
* Schema evolution of event payloads requires versioning strategy (`event_type` includes version suffix
  when payloads change: `MoneyDeposited.v2`).
* Aggregate replay performance degrades beyond ~500 events — mitigated by snapshots (see ADR-004).

## Pros and Cons of the Options

### Event Sourcing

* Good, because audit trail is authoritative — state cannot change without a stored event.
* Good, because replay is deterministic and testable — `ReplayDeterminismTest` runs on every build.
* Good, because optimistic locking is enforced by a database constraint, not application code.
* Bad, because querying current state requires maintaining a read model or replaying events.
* Bad, because event schema evolution adds operational complexity.

### CRUD + audit table

* Good, because current state is directly queryable without a read model.
* Good, because schema evolution is simpler — ALTER TABLE applies to one place.
* Bad, because the audit table is derived, not authoritative — application bugs can update state
  without writing to the audit log.
* Bad, because the audit log and the state row can diverge if the application fails mid-transaction.
* Bad, because point-in-time reconstruction requires replaying audit rows anyway, with no framework
  support.

### CRUD only

* Good, because simplest implementation.
* Bad, because there is no audit trail — fails the core financial requirement.
