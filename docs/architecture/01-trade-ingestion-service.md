# 01 — trade-ingestion-service

## What It Does

The entry point for every trade in the system. Accepts a trade request over
REST, validates it, persists it, and publishes it to Kafka for the rest of
the pipeline to pick up — all in one atomic local transaction.

| | |
|---|---|
| Port | 8086 |
| Database | trade_db (5433) |
| Publishes | `trade.submitted.v1` (JSON, via outbox) |
| Consumes | nothing |

## Why It Exists

Every trade has to enter the system somewhere, and that entry point needs to
guarantee that a trade is never accepted without also being reliably
announced to the rest of the pipeline. A trade saved to the database but
never published to Kafka would silently vanish — risk and AML would never
see it, it would never execute, and no one would know. This service's whole
job is making that impossible.

## Key Classes

**`TradeController`** — the REST entry point, `POST /api/v1/trades`. Thin —
delegates immediately to the service layer.

**`TradeValidationService`** — checks the request is well-formed (quantity
and price positive, instrument ID present, etc.) before anything is
persisted.

**`IdempotencyService`** — uses Redis `SETNX` (`setIfAbsent`) keyed by the
client-supplied `clientOrderRef`, with a 24-hour TTL. If the same
`clientOrderRef` is submitted twice, the second request is rejected before
it ever reaches the database. This is a different idempotency mechanism from
the rest of the system — every other service uses a database lookup
(`existsByTradeId()`) for idempotency. The reason: at the point a trade is
first submitted, there's no `tradeId` yet to check against — the client only
has its own reference number — so a Redis check on that reference is the
only mechanism available this early in the pipeline.

**`TradeIngestionService`** — orchestrates the above: validate, check
idempotency, create the `TradeEntity`, write it and an `OutboxEvent` in one
`@Transactional` method, return a response.

**`TradeEntity`** — not a plain data bag. It exposes a `transitionStatus()`
method that enforces a state machine over legal status transitions
(`PENDING → VALIDATED → RISK_APPROVED → AML_CLEARED → EXECUTED → SETTLING →
SETTLED` / `FAILED`), throwing `IllegalStateException` on any illegal jump.
This is "tell, don't ask" domain modelling — callers ask the entity to
change state, the entity decides if that's legal, rather than external code
freely overwriting a status field.

**`OutboxPublisher`** — polls `outbox_events` every 100ms using
`SELECT ... FOR UPDATE SKIP LOCKED`, publishes each unprocessed row to Kafka
**synchronously** (`.get()` on the send future, not `.whenComplete()`), and
only then marks it processed. The synchronous send is deliberate — an
earlier async version had a race where the 100ms poll could fire again
before the async callback marked the row processed, publishing the same
event twice. See `docs/troubleshooting/005-outbox-duplicate-events.md`.

## What Happens On a Request

1. `TradeController` receives the POST.
2. `TradeValidationService` checks the payload.
3. `IdempotencyService` checks Redis for the `clientOrderRef`. If already
   seen, reject immediately.
4. `TradeIngestionService`, in one transaction:
    - creates a `TradeEntity` with status `VALIDATED`
    - saves it
    - creates an `OutboxEvent` with the trade payload as JSON, targeting
      `trade.submitted.v1`, partitioned by `instrumentId`
    - saves that too
5. Transaction commits. Both rows exist or neither does.
6. Within ~100ms, `OutboxPublisher` picks up the outbox row and publishes it
   to Kafka, then marks it processed.

## Partitioning

Events are keyed by `instrumentId`, not `tradeId`. This means every trade
for the same instrument lands on the same Kafka partition, in submission
order — which matters because `risk-engine`'s position-limit and
concentration checks need to see trades for the same instrument in order to
be meaningful.