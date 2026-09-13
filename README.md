# Wallet Ledger Backend Service

A production-minded Java/Spring Boot wallet service implementing safe credits, debits, refunds, transfers, reward claims, idempotency, concurrency control, structured logging, metrics and an auditable transaction history.

## How to run

Requirements: Java 17+ and Maven 3.9+.

```bash
mvn clean test
mvn spring-boot:run
```

The default profile connects to PostgreSQL at `localhost:5432`. To run the full stack:

```bash
mvn clean package -DskipTests
docker compose up --build
```

Create a wallet before operating on it:

```bash
curl -X POST http://localhost:8080/api/v1/wallets/player-1
curl -X POST http://localhost:8080/api/v1/wallets/player-1/credits \
  -H 'Content-Type: application/json' \
  -d '{"requestId":"reward-1","amount":25.00,"reason":"mission reward","referenceId":"mission-42"}'
curl -X POST http://localhost:8080/api/v1/wallets/player-1/debits \
  -H 'Content-Type: application/json' \
  -d '{"requestId":"purchase-1","amount":5.00,"reason":"shop purchase","referenceId":"order-9"}'
curl -X POST http://localhost:8080/api/v1/wallets/player-1/refunds \
  -H 'Content-Type: application/json' \
  -d '{"requestId":"refund-1","originalRequestId":"purchase-1"}'
curl -X POST http://localhost:8080/api/v1/wallets/player-1/transfers \
  -H 'Content-Type: application/json' \
  -d '{"requestId":"transfer-1","toPlayerId":"player-2","amount":8.00,"reason":"gift","referenceId":"gift-9"}'
curl -X POST http://localhost:8080/api/v1/wallets/player-1/claims \
  -H 'Content-Type: application/json' \
  -d '{"requestId":"claim-1","rewardId":"reward-42","amount":15.00,"reason":"daily quest","referenceId":"quest-9"}'
curl http://localhost:8080/api/v1/wallets/player-1/balance
curl 'http://localhost:8080/api/v1/wallets/player-1/transactions?page=0&size=20'
```

## API

| Method | Endpoint | Behaviour |
|---|---|---|
| POST | `/api/v1/wallets/{playerId}` | Create an empty wallet; repeat is harmless |
| POST | `/api/v1/wallets/{playerId}/credits` | Credit and append one ledger entry |
| POST | `/api/v1/wallets/{playerId}/debits` | Debit if sufficient funds exist |
| POST | `/api/v1/wallets/{playerId}/refunds` | Refund a prior debit |
| POST | `/api/v1/wallets/{playerId}/transfers` | Transfer currency to another player |
| POST | `/api/v1/wallets/{playerId}/claims` | Claim a reward into the wallet |
| GET | `/api/v1/wallets/{playerId}/balance` | Return current balance |
| GET | `/api/v1/wallets/{playerId}/transactions` | Return Spring page of immutable entries |

Amounts use `BigDecimal` and are persisted as `NUMERIC(19,2)`. Requests require a caller-provided `requestId` that acts as the idempotency key.

OpenAPI / Swagger:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Observability

- Structured request logs include a generated `traceId` in MDC and `X-Trace-Id` response header.
- HTTP metrics are exposed through Spring Boot Actuator.
- Useful endpoints:
  - `/actuator/health`
  - `/actuator/info`
  - `/actuator/metrics`
  - `/actuator/prometheus`

## Design decisions

The wallet row is a materialized current balance, while `ledger_transactions` is the permanent audit trail. A credit/debit/refund/claim updates the balance and ledger in one database transaction, so a failure cannot leave a balance without its corresponding record. Transfer updates both wallets and writes two ledger entries atomically.

## Concurrency & Idempotency

For a balance-changing operation, the service obtains a PostgreSQL `FOR UPDATE` row lock. Therefore concurrent operations for one player are serialized and each debit evaluates the latest committed balance. Transfer locks both player wallets before applying the move. The non-negative database constraint is a final integrity guard. An optimistic `@Version` column is also retained for protection against writes that bypass the service, but the explicit pessimistic lock is the primary debit strategy because insufficient-funds decisions should not require an avoidable retry.

Idempotency is enforced by a unique `request_id` on the ledger. A replay with the same request data returns the original transaction and does not change the balance. Reusing a key with different data is rejected with `422 Unprocessable Entity`.

## Testing approach

The test suite covers replay safety, idempotency-conflict semantics, failed-debit rollback safety, paginated history ordering, concurrent wallet creation idempotency, and concurrent spend protection where only one of two simultaneous debits can succeed. It also includes controller-level tests for invalid input and error status mapping.

Concurrency tests run at the Spring/database boundary (not only in-memory locking) so race behaviour is exercised through JPA transactions.

An additional Testcontainers test (`WalletServicePostgresLockingTest`) validates debit serialization on real PostgreSQL.

Default `mvn test` runs the fast H2-backed suite and excludes Postgres-tagged tests.

Run only the PostgreSQL-tagged suite:

```bash
mvn -q -Ppostgres-tests test
```

## Assumptions and limitations

- Wallet creation is explicit; an unknown player is returned as `404`.
- Currency is fixed to one unit and amounts have two decimal places; a multi-currency ledger would need a currency column and per-currency wallet identity.
- Authentication/authorization and rate limiting are outside this small assignment.
- Concurrent collisions on the globally unique `requestId` are handled by retrying lookup after unique-key violation, returning the original transaction for identical payloads and `422` for mismatched payloads.

## AI tooling note

AI assistance was used to help scaffold and review the implementation. The important correctness decisions—database transaction boundaries, immutable ledger entries, idempotency semantics and row locking—were reviewed against the assignment requirements and covered by automated tests.
