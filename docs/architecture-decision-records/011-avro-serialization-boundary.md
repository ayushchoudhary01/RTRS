# 011 — Avro Serialization Boundary: Direct Publish vs Outbox JSON

## Status
Accepted

## Decision
Services are divided into two categories based on how they publish to Kafka:

**Category 1 — Outbox-based services (JSON strings)**
- `trade-ingestion-service`
- `trade-processor-service`

These services write business data + outbox event in a single ACID transaction.
The `OutboxPublisher` polls the outbox table and publishes to Kafka using
`StringSerializer`. The payload is a JSON string.

**Category 2 — Evaluation engines (direct Avro publish)**
- `risk-engine-service`
- `aml-engine-service`

These services publish directly to Kafka using `KafkaAvroSerializer` with
Schema Registry. No outbox table. No polling scheduler.

## Context
The outbox pattern solves the dual-write problem for transactional services —
a trade must be persisted AND an event published atomically. However, applying
the outbox pattern to every service adds unnecessary infrastructure complexity
to evaluation engines that have no transactional write requirement.

Risk-engine and AML-engine receive a trade event, run a deterministic rule chain,
and publish an outcome. If the outcome event is lost, the 30-second timeout in
`trade-processor-service`'s `ChoreographyTimeoutHandler` fires a compensating
action. The system self-heals without needing an outbox.

## Consequences for Consumers
Any consumer of a topic published by an evaluation engine **must** use
`KafkaAvroDeserializer` with `SPECIFIC_AVRO_READER_CONFIG=true`. Using
`StringDeserializer` will result in a binary parse error (CTRL-CHAR exception)
because Avro messages have a 5-byte Schema Registry header prepended.

In `trade-processor-service`, a dedicated `avroKafkaListenerContainerFactory`
bean handles this:

```java
props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
```

Consumers of outbox-published topics (`trade.submitted.v1`, `trade.rejected.v1`,
`trade.executed.v1`) continue to use `StringDeserializer` + Jackson JSON parsing.

## Serialization Boundary Summary
| Topic | Publisher | Serializer | Consumer Deserializer |
|---|---|---|---|
| trade.submitted.v1 | trade-ingestion (outbox) | StringSerializer | StringDeserializer |
| trade.rejected.v1 | trade-processor (outbox) | StringSerializer | StringDeserializer |
| trade.executed.v1 | trade-processor (outbox) | StringSerializer | StringDeserializer |
| risk.approved.v1 | risk-engine (direct) | KafkaAvroSerializer | KafkaAvroDeserializer |
| risk.breached.v1 | risk-engine (direct) | KafkaAvroSerializer | KafkaAvroDeserializer |
| aml.cleared.v1 | aml-engine (direct) | KafkaAvroSerializer | KafkaAvroDeserializer |
| aml.flagged.v1 | aml-engine (direct) | KafkaAvroSerializer | KafkaAvroDeserializer |

## Alternatives Rejected
- **Avro everywhere** — would require every service to have a Schema Registry
  producer config and eliminate the outbox pattern's atomicity guarantee for
  transactional services.
- **JSON everywhere** — loses compile-time safety, schema enforcement, and
  binary efficiency for evaluation engine events.