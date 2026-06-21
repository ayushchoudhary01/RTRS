# Debezium CDC — Not Yet Implemented

This folder contains placeholder filenames only — `OutboxEventRouterTransform.java`,
connector configs for ledger/audit CDC, and a Debezium-specific compose file —
all currently empty.

The system currently uses application-level outbox polling
(`OutboxPublisher` with `FOR UPDATE SKIP LOCKED`, implemented per-service) for
trade-ingestion-service, trade-processor-service, ledger-service, and
settlement-service. This works correctly but has ~100ms polling latency and a
scheduler thread per service.

This folder is also the architectural successor to a separately-planned
`outbox-relay-service`, which was never built — see the note in the future
doc below for why a standalone relay service was rejected in favor of either
per-service polling (current state) or Debezium CDC (this folder's intent).

See [`docs/future/001-debezium-cdc.md`](../../docs/future/001-debezium-cdc.md)
for the full planned migration: WAL-based CDC reading directly from each
service's PostgreSQL instance, eliminating the polling scheduler entirely.