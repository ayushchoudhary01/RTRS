# 006 — LGTM Observability Stack

## Status
Accepted

## Decision
The observability stack consists of Loki (logs), Grafana (dashboards), Tempo (traces), and Prometheus (metrics). This replaces the legacy ELK + Zipkin combination.

## Context
We need metrics, distributed tracing, and log aggregation across 15 microservices. All three pillars must be correlated — clicking a metric spike should jump to the relevant trace, which should link to the relevant logs.

## Reasons
- **Single UI** — all four pillars (metrics, traces, logs, dashboards) live in Grafana. No switching between Kibana, Zipkin, and Grafana.
- **Correlation** — Grafana natively correlates Tempo traces with Loki logs using `traceId`. One click from metric spike → trace → logs.
- **Loki vs ELK** — Loki uses label-based indexing (like Prometheus) rather than full-text indexing. Dramatically cheaper on storage and CPU. ELK is RAM-hungry and operationally painful.
- **Tempo vs Zipkin** — Tempo supports TraceQL for powerful trace queries. Zipkin is in maintenance mode with limited query capability.
- **OpenTelemetry** — Tempo is the recommended backend for OpenTelemetry traces, which is now the industry standard instrumentation framework.

## Alternatives Rejected
- **ELK Stack** — Elasticsearch is resource-intensive, Kibana is a separate UI, no native trace correlation.
- **Zipkin** — largely in maintenance mode, no TraceQL, weaker Grafana integration.
- **Jaeger** — good but Tempo is more storage-efficient and Grafana-native.

## Consequences
- `traceId` must be propagated via Kafka message headers and HTTP headers across all services.
- Each service exposes `/actuator/prometheus` for Prometheus scraping.
- Structured JSON logging required for Loki label extraction.
