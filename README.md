# banking-ledger

A double-entry banking ledger and payments service in Spring Boot 3 / Java 21 / PostgreSQL.

Built as a portfolio piece around the engineering problems that actually matter in money movement: correct accounting, concurrency-safe balance updates, idempotent retries, atomic transfers.

## Status

Phases 1–3 complete: domain layer, money movement endpoints, pessimistic-locking-based concurrency control, idempotency, Testcontainers-backed concurrency stress test, Micrometer instrumentation, and a provisioned Prometheus + Grafana stack. See `prompt.md` for the full brief.

## Concurrency proof

The headline test in `ConcurrentTransferStressTest` fires **1,000 concurrent transfers from a single source account** (20 threads × 50 transfers each) against a real Postgres via Testcontainers, and asserts the final balance is **exactly** correct — zero lost updates, zero double-spends. A second test runs 16 threads doing bidirectional A↔B transfers to verify the deterministic lock ordering prevents deadlocks.

## Architecture

```mermaid
flowchart LR
    client[HTTP client]

    subgraph app[Spring Boot service]
        ctrl[Controllers]
        svc["TransactionService — @Transactional + SELECT FOR UPDATE"]
        repo_jpa[JPA repositories]
        adv["GlobalExceptionHandler — @RestControllerAdvice"]
        meter[Micrometer counters]
        prom_ep[/actuator/prometheus/]

        ctrl --> svc
        svc --> repo_jpa
        adv -..-> ctrl
        meter -..-> svc
        meter -..-> adv
        prom_ep -..-> meter
    end

    repo_jpa --> db[(PostgreSQL 16 — accounts / transactions / ledger_entries)]

    client --> ctrl
    prometheus[Prometheus] -- scrape every 5s --> prom_ep
    grafana[Grafana provisioned dashboard] --> prometheus
```

### Transfer flow

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant API as TransferController
    participant S as TransactionService
    participant DB as Postgres

    C->>API: POST /transfers + Idempotency-Key
    API->>S: transfer(from, to, amount, key)
    S->>DB: SELECT * FROM transactions WHERE idempotency_key = ?
    alt key already used
        DB-->>S: existing transaction
        S-->>C: 200 with original txn (no double-spend)
    else fresh request
        S->>DB: SELECT * FROM accounts WHERE id = lo FOR UPDATE
        S->>DB: SELECT * FROM accounts WHERE id = hi FOR UPDATE
        Note over S,DB: deterministic lo-then-hi order prevents deadlocks
        S->>DB: INSERT transactions (PENDING)
        S->>DB: INSERT ledger_entries (DEBIT from, CREDIT to)
        S->>DB: UPDATE transactions SET status = COMPLETED
        DB-->>S: commit
        S-->>C: 200 with new txn
    end
```

### Domain

```mermaid
erDiagram
    accounts ||--o{ ledger_entries : "history"
    transactions ||--|{ ledger_entries : "balanced pair"

    accounts {
        UUID id PK
        VARCHAR owner
        VARCHAR currency
        TIMESTAMPTZ created_at
    }
    transactions {
        UUID id PK
        VARCHAR type "DEPOSIT | WITHDRAWAL | TRANSFER"
        VARCHAR status "PENDING | COMPLETED | FAILED"
        VARCHAR idempotency_key "UNIQUE, nullable"
        TIMESTAMPTZ created_at
    }
    ledger_entries {
        UUID id PK
        UUID account_id FK
        UUID transaction_id FK
        VARCHAR entry_type "DEBIT | CREDIT"
        BIGINT amount "minor units"
        BIGINT balance_after
        TIMESTAMPTZ created_at
    }
```

## Stack

Java 21 · Spring Boot 3.3 · Spring Data JPA · Spring Security · PostgreSQL 16 · Flyway · Lombok · MapStruct · springdoc-openapi · Micrometer + Prometheus + Grafana · JUnit 5 · Testcontainers.

## Running locally

```bash
docker compose up -d                 # postgres + prometheus + grafana
mvn spring-boot:run                  # the service itself
./ops/load.sh                        # optional: generate dashboard traffic
```

| What | Where |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Actuator | http://localhost:8080/actuator |
| Prometheus scrape | http://localhost:8080/actuator/prometheus |
| Prometheus UI | http://localhost:9090 |
| Grafana | http://localhost:3000 (anonymous viewer; admin/admin) |

## Observability

The service emits three custom Micrometer counters in addition to the default JVM / HTTP / DataSource metrics:

| Metric | Tags | Where it's incremented |
|---|---|---|
| `transactions_completed_total` | `type` ∈ {deposit, withdrawal, transfer} | service layer, after `status=COMPLETED` |
| `transactions_failed_total` | `reason` ∈ {insufficient_funds, currency_mismatch, bad_request} | `@RestControllerAdvice` |
| `idempotent_duplicates_blocked_total` | `type` | service layer, when the Idempotency-Key short-circuits |

The Grafana dashboard at `ops/grafana/dashboards/banking-ledger.json` is auto-provisioned and renders these as five panels: completed rate by type, failed rate by reason, blocked-duplicate counter, HTTP-status rates, and request-latency p95 by URI.

![Grafana dashboard](docs/grafana-dashboard.png)

## Running tests

```bash
mvn verify
```

The Testcontainers tests spin up a real Postgres 16 container — no H2 cheating.

> **Local note for macOS Docker runtimes:** Docker 29 daemons (Colima, OrbStack) require API ≥ 1.40, but Testcontainers' shaded docker-java still negotiates 1.32. Colima additionally can't start the Testcontainers ryuk reaper under its default mount config. Both fixes are already wired into `pom.xml` via surefire env vars: `DOCKER_API_VERSION=1.43`, `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock`, and `TESTCONTAINERS_RYUK_DISABLED=true`. No action needed; documenting because it cost an afternoon to root-cause. If you switch runtimes, `rm ~/.testcontainers.properties` to clear Testcontainers' cached strategy.

## Design decisions

- **Pessimistic locking (`SELECT FOR UPDATE`)** for balance updates. Chosen over optimistic `@Version` for simpler reasoning and closer fit to how some banks model the same problem. Tradeoff: lower throughput under contention; acceptable here.
- **Deterministic lock ordering** (lower UUID first) on transfers so two concurrent transfers between the same pair of accounts in opposite directions can't deadlock. Proven by `ConcurrentTransferStressTest.concurrentBidirectionalTransfersDoNotDeadlock`.
- **Money as `BIGINT` minor units** (not `BigDecimal`). Exact, no rounding, no representation surprises.
- **True double-entry**: every transaction writes a balanced debit + credit pair. Deposits and withdrawals use a per-currency `SYSTEM` cash account as the counterparty.
- **Balance derived from `SUM(ledger_entries)`**, never stored as a mutable column. The pessimistic lock on the account row guarantees the sum is read against a consistent slice.
- **`Idempotency-Key` header** on every money-movement endpoint, deduped via a `UNIQUE` constraint on `transactions.idempotency_key` — same pattern as Stripe's API.
- **Errors as structured `ApiError` payloads** with correct HTTP semantics (422 for business-rule violations, 400 for bad input, 404 for missing entities).
