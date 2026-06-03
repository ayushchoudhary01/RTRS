# 001 — Why Kafka as the Message Broker

## Status
Accepted

## Decision
Apache Kafka is used as the sole message broker across all RTRS microservices.

## Context
RTRS is an event-driven system where trade events must flow through multiple services — risk engine, AML engine, ledger, settlement — in real time. We needed a message broker that could handle high throughput, guarantee ordering, and support event replay.

## Reasons
- **Event replay** — Kafka retains messages for a configurable period. If the ledger service goes down and comes back, it can replay missed events from its last committed offset. RabbitMQ deletes messages on consume — no replay possible.
- **Partition-based ordering** — trades are partitioned by `instrumentId`, guaranteeing strict ordering per instrument without manual thread locking.
- **Consumer groups** — multiple services can independently consume the same topic without interfering with each other.
- **Schema Registry integration** — Kafka works natively with Confluent Schema Registry for Avro schema versioning.
- **Fault tolerance** — with 3 brokers, replication factor 3, and min ISR 2, we can lose one broker with zero data loss.

## Alternatives Rejected
- **RabbitMQ** — no message replay, no partition-based ordering, not designed for event sourcing patterns.
- **ActiveMQ** — legacy technology, poor ecosystem for modern cloud-native systems.
- **Redis Pub/Sub** — fire-and-forget, no persistence, no consumer groups, no replay.

## Consequences
- Requires Schema Registry as an additional infrastructure component.
- Higher local dev resource usage (3 brokers).
- Consumers must handle idempotency since Kafka guarantees at-least-once delivery.
