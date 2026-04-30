# LedgerFlow — System Context (C4 Level 1)

```mermaid
C4Context
    title LedgerFlow System Context

    Person(holder, "Account Holder", "Creates accounts, deposits and withdrawals")
    Person(auditor, "Finance Auditor", "Audits event history and read model")
    Person(admin, "System Admin", "Monitors and triggers read model rebuild")

    System(sys, "LedgerFlow", "Immutable financial ledger, CQRS Event Sourcing")

    System_Ext(prom, "Prometheus", "Scrapes Micrometer metrics")
    System_Ext(ci, "GitHub Actions", "Runs CI build and test on every push")

    Rel(holder, sys, "Creates accounts and transfers", "REST HTTP")
    Rel(auditor, sys, "Reads event history", "REST HTTP")
    Rel(admin, sys, "Triggers rebuild", "REST HTTP")
    Rel(sys, prom, "Exposes metrics", "HTTP actuator")
    Rel(ci, sys, "Builds and tests", "mvn verify")
```

## Scope

LedgerFlow is a portfolio project demonstrating CQRS and Event Sourcing with Java 21 and Spring Boot 3.3. Every financial operation is stored as an immutable event; current account state is rebuilt entirely from event replay.

## Personas

| Persona | Description |
|---------|-------------|
| Account Holder | Creates accounts, deposits, withdrawals, and transfers |
| Finance Auditor | Reads full event history, validates read model consistency |
| System Admin | Triggers read model rebuild via admin endpoint |

## External Systems

| System | Role |
|--------|------|
| Prometheus | Scrapes `/actuator/prometheus` for Micrometer metrics |
| GitHub Actions | Runs `mvn verify` on every push including OWASP dependency check |
