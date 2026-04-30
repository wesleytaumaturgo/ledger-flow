# LedgerFlow — Container Diagram (C4 Level 2)

```mermaid
C4Container
    title LedgerFlow Container Diagram

    Person(user, "Users", "Account holders and administrators")

    System_Boundary(sys, "LedgerFlow") {
        Container(api, "REST API", "Spring Boot 3.3 Java 21", "Handles all HTTP requests")
        ContainerDb(es, "Event Store", "PostgreSQL 16 JSONB", "Append-only event log")
        ContainerDb(rm, "Read Models", "PostgreSQL 16 JPA", "Denormalized projections for queries")
    }

    System_Ext(prom, "Prometheus", "Metrics collection")
    System_Ext(ci, "GitHub Actions", "CI build and test pipeline")

    Rel(user, api, "Uses", "REST HTTP")
    Rel(api, es, "Appends and replays events", "JDBC")
    Rel(api, rm, "Reads balance and history", "JPA")
    Rel(api, rm, "Updates via projector", "In-process events")
    Rel(api, prom, "Exposes metrics", "HTTP actuator")
    Rel(ci, api, "Builds and tests", "mvn verify")
```

## Notes

- The REST API is a single Spring Boot 3.3 application running on Java 21 Virtual Threads.
- Event Store and Read Models share the same PostgreSQL 16 instance but are logically separated: `event_store` table is append-only; read model tables are updated by projectors.
- Projectors listen to Spring in-process events published by `PostgresEventStore` after each successful write. The publish happens inside the same transaction boundary as the event insert.
- `CorrelationIdFilter` injects a `traceId` into MDC for all requests and clears it in `finally`.

## Containers

| Container | Technology | Responsibility |
|-----------|------------|----------------|
| REST API | Spring Boot 3.3, Java 21 | Command and query endpoints, event publishing, projectors |
| Event Store | PostgreSQL 16, JSONB | Append-only source of truth for all domain events |
| Read Models | PostgreSQL 16, Spring Data JPA | Denormalized account summaries and transaction history |
