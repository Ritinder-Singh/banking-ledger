# banking-ledger

A double-entry banking ledger and payments service in Spring Boot 3 / Java 21 / PostgreSQL.

Built as a portfolio piece around the engineering problems that actually matter in money movement: correct accounting, concurrency-safe balance updates, idempotent retries, atomic transfers.

## Status

Phase 0 — project skeleton. No domain logic yet. See `prompt.md` for the full brief and build phases.

## Stack

Java 21 · Spring Boot 3.3 · Spring Data JPA · Spring Security · PostgreSQL 16 · Flyway · Lombok · MapStruct · springdoc-openapi · Micrometer/Prometheus · JUnit 5 · Testcontainers.

## Running locally

```bash
docker compose up -d postgres
mvn spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Actuator: http://localhost:8080/actuator
- Prometheus scrape: http://localhost:8080/actuator/prometheus

## Design decisions (so far)

- **Pessimistic locking (`SELECT FOR UPDATE`)** for balance updates. Chosen over optimistic `@Version` for simpler reasoning and closer fit to how some banks model the same problem. Tradeoff: lower throughput under contention; acceptable here.
- **Balance derived from ledger entries**, never stored as a mutable column. Every transaction writes a balanced debit + credit pair inside one `@Transactional` boundary.
- **Idempotency-Key header** on transfers, with server-side dedupe — same pattern as Stripe's API.

More to come as phases land.
