# Grafana Dashboards — Not Yet Built

The JSON files in this folder (`trade-throughput.json`, `risk-exposure.json`,
`aml-alerts.json`, `settlement-lag.json`, `outbox-queue-depth.json`,
`trace-explorer.json`) are empty placeholders naming the dashboards that would
be useful to build, but none have been created.

Grafana is running (`docker-compose.yml`) with Prometheus, Loki, and Tempo
wired as datasources (see `../datasources/`), and Prometheus is actively
scraping real metrics from every RTRS service's Spring Actuator endpoint
(see `../prometheus/prometheus.yml`). The metrics exist; the dashboards
visualizing them do not yet.

Each filename here describes its intended purpose:
- `trade-throughput.json` — trades/sec, broken down by status (submitted,
  rejected, executed)
- `risk-exposure.json` — notional exposure by instrument, breach rate over time
- `aml-alerts.json` — flagged trade rate, risk score distribution, rule
  trigger frequency
- `settlement-lag.json` — time from trade execution to settlement, broken
  down by status (settled/failed/escalated), EOD batch run duration
- `outbox-queue-depth.json` — unprocessed outbox row count per service, a
  direct signal for outbox publisher health
- `trace-explorer.json` — Tempo trace search/waterfall view, depends on
  distributed tracing actually being wired (see
  `docs/future/012-distributed-tracing.md`)

These can be built directly against Prometheus metrics already being
collected — this is genuinely low-effort relative to value if picked back up,
since the data pipeline (Actuator → Prometheus → Grafana) is already working
end to end.