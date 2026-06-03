# 007 — Avro Schema Versioning with BACKWARD Compatibility

## Status
Accepted

## Decision
All Kafka events are serialized using Apache Avro. Schemas are registered in Confluent Schema Registry with BACKWARD compatibility enforced. Breaking changes create a new topic version, never a schema mutation.

## Context
In an event-driven system, producers and consumers of Kafka topics evolve independently. A schema change that breaks existing consumers is a production incident.

## Reasons
- **BACKWARD compatibility** — a consumer using schema v1 can read messages produced with schema v2, as long as new fields have defaults. This allows consumers to be upgraded independently of producers.
- **Schema Registry as contract** — the registry is the single source of truth for event contracts. Any service can look up "what does TradeSubmittedEvent v1 look like?" at any time.
- **Avro binary format** — 3-5x smaller than JSON. Matters at high trade throughput.
- **Compile-time safety** — Avro Maven plugin generates Java classes from schemas. Using the wrong field type is a compile error, not a runtime error.

## Rules
- New fields must always have a default value (`null` or explicit).
- Never remove or rename existing fields — this is a breaking change.
- Breaking changes → new topic (`trade.submitted.v2`), not a schema mutation.
- All schemas use `precision: 38, scale: 10` for monetary values to avoid floating-point precision issues.

## Alternatives Rejected
- **JSON** — no schema enforcement, no binary efficiency, no compile-time safety.
- **Protobuf** — also valid, but Avro has better Confluent Schema Registry integration.

## Consequences
- Confluent Schema Registry must be running before any service starts.
- Shared `rtrs-events` library must be updated and republished for schema changes.
