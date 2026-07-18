# Real-Time Payment Processing System

A production-grade payment processing backend built with Java and Spring Boot, demonstrating core banking engineering patterns: double-entry bookkeeping, idempotent transaction processing, deadlock-free concurrent locking, and automated ledger reconciliation.

## Architecture

```
REST API (Spring MVC)
    │
    ├── Idempotency Layer      SHA-256 request hashing, 24hr TTL deduplication
    │
    ├── Transaction Service    SERIALIZABLE isolation, pessimistic row locking
    │       │
    │       └── Ledger Service Double-entry journal entries (DEBIT + CREDIT)
    │
    ├── Reconciliation Service REPEATABLE_READ isolation, balance verification
    │
    └── Event Publisher        Redis Pub/Sub → fraud-detection pipeline
```

## Key Engineering Decisions

**Double-entry bookkeeping** — Every transaction produces exactly two ledger entries (one DEBIT, one CREDIT) that sum to zero. Account balances are derived from the ledger, not stored directly, making the system auditable and tamper-evident.

**Deadlock prevention** — Concurrent transfers that share accounts acquire row locks in consistent UUID order (lowest UUID first). This eliminates circular wait conditions without retry overhead.

**Idempotency** — Each request is fingerprinted with SHA-256. Retried requests with the same key return the cached response without reprocessing, eliminating duplicate charges on network failures.

**SERIALIZABLE isolation** — All financial writes run under PostgreSQL's SERIALIZABLE isolation level, preventing phantom reads and write skew anomalies that could corrupt account balances.

**Automated reconciliation** — A scheduled job recomputes every account balance from raw ledger entries and flags any discrepancy between the computed and stored balance.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Caching / Events | Redis (Pub/Sub) |
| Retry | Spring Retry (exponential backoff) |
| Testing | JUnit 5, H2 (in-memory) |
| Infrastructure | Docker, Docker Compose |

## Performance

Benchmarked with Apache Bench (500 requests, 20 concurrent connections):

- **1,800+ requests/sec** throughput
- **p50 latency: 9ms**, p99 latency: 29ms
- 0 failed requests

## Getting Started

**Prerequisites:** Docker, Docker Compose

```bash
# Clone and start infrastructure
git clone https://github.com/KevinLanahan/payment-processor
cd payment-processor
docker compose up -d

# Verify it's running
curl http://localhost:8080/actuator/health
```

## API

### Create an account
```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"ownerName": "Alice", "currency": "USD", "initialBalance": 10000}'
```

### Transfer funds
```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: txn-001" \
  -d '{
    "sourceAccountId": "<source-id>",
    "destAccountId": "<dest-id>",
    "amount": 500.00,
    "currency": "USD",
    "type": "TRANSFER"
  }'
```

### Run reconciliation
```bash
curl http://localhost:8080/api/v1/reconciliation/run
```

## Running Tests

```bash
./mvnw test
```

Tests cover double-entry invariants, insufficient funds, suspended account rejection, deadlock-free locking, idempotency deduplication, and reconciliation discrepancy detection.

## Related Projects

- [fraud-detection](https://github.com/KevinLanahan/fraud-detection) — consumes transaction events from this service via Redis Pub/Sub
- [portfolio-risk](https://github.com/KevinLanahan/portfolio-risk) — standalone portfolio risk calculator
