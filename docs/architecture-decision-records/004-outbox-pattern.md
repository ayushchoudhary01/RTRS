# 004 — Transactional Outbox Pattern

## Status
Accepted

## Decision
No service publishes events directly to Kafka. Every event is first written to an `outbox_events` table in the same database transaction as the business data, then relayed to Kafka by a dedicated publisher.

## Context
The dual-write problem: if a service writes to the database and then publishes to Kafka, either operation can fail independently. A crash between the two leaves the system in an inconsistent state — data saved but event never published, or event published but data not saved.

## Reasons
- **Atomicity** — writing business data and the outbox event in a single ACID transaction guarantees both succeed or both fail. No partial state.
- **At-least-once delivery** — the outbox publisher retries failed Kafka publishes until they succeed. Combined with consumer-side idempotency, this achieves effectively-once semantics.
- **No data loss** — even if Kafka is temporarily down, events are safely stored in the outbox table and published when Kafka recovers.

## Alternatives Rejected
- **Direct Kafka publish** — not atomic with the database write, prone to dual-write failures.
- **Two-phase commit (2PC)** — distributed transactions across DB and Kafka are extremely complex and fragile.

## Evolution Path
Replace the polling `OutboxPublisher` with Debezium CDC reading PostgreSQL WAL directly. This eliminates polling overhead and achieves near-zero latency event publishing.

## Consequences
- Each service requires an `outbox_events` table.
- Outbox publisher must handle deduplication and retry logic.
- Slight increase in database write load.
