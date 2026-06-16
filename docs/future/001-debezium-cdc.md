# 001 — Debezium CDC: Replace Outbox Polling

## Current State
`OutboxPublisher` polls the outbox table every 100ms using `FOR UPDATE SKIP LOCKED`.
Works correctly but has inherent latency and adds scheduler overhead to each service.

## Planned Improvement
Replace the polling scheduler with Debezium CDC reading PostgreSQL WAL directly.

```
Business Transaction
↓
INSERT into outbox_events
↓
Commit
↓
Debezium reads WAL (near real-time)
↓
Kafka
```

## Benefits
- Sub-millisecond latency vs 100ms polling window
- No scheduler thread per service
- Horizontal scaling — multiple Debezium connectors can run safely
- Zero load on application — no polling queries
- Production pattern used by major banks

## Implementation Notes
- `OutboxEventRouterTransform.java` already exists in infrastructure folder
- Debezium PostgreSQL connector config needed per service DB
- `cdc-relay-service` is already planned in the architecture (no-DB service)
- Each service DB needs `wal_level = logical` in PostgreSQL config
- Outbox table structure stays the same — only the relay mechanism changes


