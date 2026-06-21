# 012 — Distributed Tracing: End-to-End Trace Propagation via Tempo

## Current State
- `micrometer-tracing-bridge-otel` and `opentelemetry-exporter-otlp` are already
  dependencies in every service's `build.gradle.kts`.
- Every built service's `application.yaml` already has the tracing config:
  ```yaml
  management:
    tracing:
      sampling:
        probability: 1.0
    otlp:
      tracing:
        endpoint: http://localhost:4318/v1/traces
  ```
- The log pattern already includes `[traceId=%X{traceId}]` reading from MDC.
- Tempo is in `docker-compose.yml` with ports 3200 (HTTP API), 4317 (OTLP gRPC),
  4318 (OTLP HTTP) exposed.

## Already Fixed
While investigating this, a real bug was found and corrected: the Tempo
container's volume mount pointed at a filename (`tempo.yml`) that didn't match
the actual file on disk (`tempo-config.yml`), and the file itself was 0 bytes —
part of the broader empty `infrastructure/` scaffolding inherited from the
original architecture plan. This was fixed with a real single-binary-mode
config (OTLP receivers on 4317/4318, local trace storage, 24h compaction
retention). Tempo now starts cleanly with valid config. This was the only part
of this work actually completed before the rest was deliberately deferred.

## What's Left To Do

### 1. Verify auto-instrumentation works for simple HTTP spans
Spring Boot + Micrometer Tracing auto-configures span creation and OTLP export
for incoming HTTP requests with zero additional code. Need to confirm a single
`POST /api/v1/trades` produces a visible trace in Tempo's UI before assuming
anything deeper works.

### 2. Solve the outbox thread hand-off problem (the hard part)
Trace context is thread-local. The flow that breaks it:
```
HTTP request thread (trace context exists)
↓
Save trade + outbox event in one transaction
↓
HTTP response returned, request thread context discarded
↓
[100ms later, different thread]
↓
@Scheduled OutboxPublisher thread (no trace context — NEW trace or none)
↓
kafkaTemplate.send() — auto-instrumentation only sees the publisher's context,
not the original request's
```
The trace that shows up in Tempo for the Kafka publish will NOT be linked to
the original HTTP request's trace unless context is explicitly captured and
restored.

**Approach:** capture the current `traceId`/`spanId` (or full W3C traceparent)
at outbox event creation time, store it as a column on the outbox row
(`trace_context VARCHAR`), and when `OutboxPublisher` sends the Kafka message,
explicitly set that captured context before calling `kafkaTemplate.send()` so
the Kafka producer interceptor picks it up and injects the correct headers.

### 3. Verify cross-service propagation through Kafka
Spring Kafka's auto-instrumentation should extract trace context from
incoming Kafka message headers and continue the trace in `@KafkaListener`
methods, without code changes — this part is expected to "just work" once
step 2 makes the published message carry the right headers.

### 4. End-to-end verification
Send one trade through `trade-ingestion-service`. In Tempo's UI, search by the
trace ID logged at the HTTP layer and confirm a single connected trace shows:
trade-ingestion (HTTP + outbox publish) → trade-processor (consume +
choreography) → risk-engine (consume + evaluate + publish) → aml-engine
(consume + evaluate + publish) → trade-processor (consume both, execute) →
ledger-service (consume + double-entry write) → settlement-service (consume +
instruction creation).

## Why This Was Deferred
This is genuinely valuable — without it, the LGTM stack's observability story
is only partially true: Prometheus metrics flow via Spring Actuator, but Tempo
and Loki are running containers with no real data flowing through them yet.
However, by the time this was reached, six services were already built and
verified end-to-end with real concurrency handling, choreography sagas, CQRS,
double-entry bookkeeping, hash chains, and Spring Batch settlement processing.
Tracing is infrastructure polish on a system that already proves its core
engineering claims. Time was better spent finishing settlement-service and
reconciliation-service than wiring observability plumbing on top of an
already-substantial project. This was a deliberate scope decision, not an
oversight — the investigation was done far enough to know exactly what's
involved and where the one genuinely hard part is.


