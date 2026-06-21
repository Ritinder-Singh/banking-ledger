# Core Banking Ledger & Payments Service — Project Brief

## Overview

A Spring Boot service that models a simplified bank ledger: accounts, deposits, withdrawals, and transfers, built around correct double-entry accounting and concurrency-safe money movement. The goal isn't novelty — it's proving you can write Java the way a regulated, production environment needs it written: correct, tested, observable, and safe under concurrent load.

## Why This Project

- **Domain-relevant** for the roles you're targeting (Citi, other banks, fintechs, trading platforms) — the "money movement" framing is literally what their systems do.
- **Forces real engineering problems**, not CRUD-tutorial problems: race conditions, idempotency, transactional atomicity. These are the things that come up in actual bank interview loops.
- **Reuses skills you already have** (CI/CD, observability, distributed systems thinking from Genius365/PixelPod) — you're porting muscle memory to a new language, not starting cold.

## Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Language | Java 21 (LTS) | What enterprises actually run |
| Framework | Spring Boot 3.x (Web, Data JPA, Validation, Actuator) | Industry default |
| Build tool | Maven | Most banks use Maven, not Gradle — matches what you'll see day one |
| Database | PostgreSQL + Flyway migrations | Real DB, real migration discipline |
| Testing | JUnit 5, Mockito, Testcontainers | Testcontainers = tests against a real Postgres, not H2 — a real signal |
| Observability | Micrometer + Prometheus + Grafana | You already know this stack from PixelPod |
| CI/CD | GitHub Actions | Same muscle as your Genius365 CI/CD work |
| Containerization | Docker + docker-compose | |
| Stretch | Kafka (async events), Resilience4j (rate limiting) | Optional, see below |

## Domain Model

- **Account** — id, owner, currency, `@Version` column for optimistic locking
- **Transaction** — id, type (DEPOSIT / WITHDRAWAL / TRANSFER), status, idempotency_key, created_at
- **LedgerEntry** — id, account_id, transaction_id, type (DEBIT / CREDIT), amount, balance_after, created_at

**Key principle:** every transaction writes a balanced debit + credit pair to the ledger. Balance is computed from ledger history, not a mutable column you increment/decrement directly. This is the textbook-correct way real financial systems work — and being able to explain *why* in an interview is worth more than the code itself.

## API Surface

```
POST   /accounts                          create account
GET    /accounts/{id}                     get account + current balance
GET    /accounts/{id}/transactions        paginated ledger history
POST   /accounts/{id}/deposit
POST   /accounts/{id}/withdraw
POST   /transfers                         body: fromAccountId, toAccountId, amount
                                           header: Idempotency-Key
GET    /transactions/{id}                 transaction status
```

## The Engineering Problems That Actually Matter

1. **Concurrency-safe balance updates** — use `@Version` (optimistic locking) with retry-on-conflict, or pessimistic `SELECT FOR UPDATE`. Pick one, document the tradeoff in your README.
2. **Idempotent transfers** — dedupe on `Idempotency-Key` so a retried request can't double-process a payment. This is the exact pattern Stripe's API uses.
3. **Atomicity** — debit + credit happen inside one `@Transactional` boundary; either both happen or neither does.
4. **Consistent error model** — `@ControllerAdvice` exception handler, meaningful HTTP status codes, no leaking stack traces.
5. *(Stretch)* Hand-rolled token-bucket rate limiter — a clean, small way to show Data Structures & Algorithms applied to a real problem, not just a resume keyword.
6. *(Stretch)* Kafka producer emitting a `transaction.completed` event — demonstrates event-driven architecture in Java, same concept you already have on your resume in Python.

## Testing Strategy

- Unit tests on the service layer (mocked repositories)
- **The test that matters most:** an integration test using Testcontainers that fires N concurrent transfer requests at the same account and asserts the final balance is exactly correct — proof your locking strategy actually works, not just that it compiles.
- API-level tests asserting response shape and status codes

## Observability & CI/CD

- Spring Actuator health/metrics endpoints
- Custom Micrometer counters: `transactions.completed`, `transactions.failed`, `idempotent.duplicates.blocked`
- GitHub Actions: build → test (with a Postgres service container) → Docker build
- README includes a screenshot of the Grafana dashboard and an architecture diagram

## Suggested Repo Structure

```
core-ledger/
  src/main/java/.../account/
  src/main/java/.../ledger/
  src/main/java/.../transaction/
  src/main/java/.../common/exception/
  src/test/...
  docker-compose.yml
  Dockerfile
  .github/workflows/ci.yml
  README.md
```

## Loose Build Phases

1. **Core domain** — entities, CRUD endpoints, Postgres, basic validation
2. **The hard part** — concurrency control, idempotency, the concurrency test
3. **Production shape** — observability, Docker, CI/CD
4. **Polish** — README with architecture diagram, OpenAPI/Swagger docs, Postman collection

## What This Buys You on a Resume

- "Built a transactional ledger system with optimistic-locking-based concurrency control, eliminating race conditions on concurrent balance updates"
- "Implemented idempotent payment processing using idempotency keys to guarantee exactly-once semantics under client retries"
- "Designed a double-entry ledger schema with full audit trail and Testcontainers-based concurrency verification"

## Reusability Across Other Java Jobs

Yes, and deliberately so. The "banking" framing is a bonus for Citi, other banks, fintechs, and trading platforms — but the underlying skills demonstrated are the generic core of almost every Spring Boot job posting in India, finance or not: REST APIs, JPA/Hibernate, Postgres, concurrency, automated testing, Docker, CI/CD. That's the same stack whether the job is a bank, an e-commerce backend, or a SaaS product company.

For non-finance roles, you don't rebuild anything — you just reframe the pitch: lead with "transactional system design," "concurrency-safe state management," and "idempotent API design" instead of "banking domain." Same project, two elevator pitches depending on who's reading the resume.