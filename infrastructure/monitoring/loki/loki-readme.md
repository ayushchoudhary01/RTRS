# Loki Log Aggregation — Running on Bundled Defaults

`loki-config.yml` and `promtail-config.yml` in this folder are empty and
**not mounted into the Loki container** — check `docker-compose.yml`, the
`loki` service has no `volumes:` entry. Loki currently runs entirely on its
own bundled default config (`-config.file=/etc/loki/local-config.yaml`,
which is the image's internal default file, not anything from this repo).

This means log aggregation into Loki is not actually wired up yet — there is
no Promtail (or equivalent) container shipping service logs into Loki. Each
service's logs are currently viewed directly via console/IntelliJ output,
which is sufficient for local development.

To make this real: add a Promtail container to `docker-compose.yml` scraping
each service's log output (or stdout via Docker's own log driver), configure
`promtail-config.yml` with the actual scrape targets, and mount a real
`loki-config.yml` if default retention/storage settings need to change.

Not currently planned as a near-term improvement — logs are sufficiently
accessible via each service's own output for a project at this stage. If
distributed tracing (see `docs/future/012-distributed-tracing.md`) is ever
completed, wiring Loki for log-trace correlation in Grafana would be a
natural follow-up, since the Tempo Grafana datasource is already pre-configured
with a `tracesToLogsV2` link to a Loki datasource named `loki`.