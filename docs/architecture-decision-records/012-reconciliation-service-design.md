# 012 — Reconciliation Service: Read-Only Sink, No Outbox, No Avro

## Status
Accepted

## Decision
`reconciliation-service` consumes `trade.executed.v1` (JSON, from trade-processor's
outbox) and `ledger.entry.created.v1` (JSON, from ledger-service's outbox) and
builds an independent fact-based view to detect discrepancies between the two
sources of truth. It never publishes to Kafka and has no outbox table.

## Context
Every other service built so far either writes business state atomically with
an outbox event (trade-ingestion, trade-processor, ledger-service) or evaluates
and publishes directly via Avro (risk-engine, aml-engine). Reconciliation-service
is architecturally different: it is a pure sink. Its entire job is to compare
two independent event streams and surface discrepancies — it produces no event
that any other service needs to react to in real time.

## Design
### Fact tables, not a copy of source state
`reconciliation_trade_facts` and `reconciliation_ledger_facts` are deliberately
minimal projections — just enough fields to detect breaks (quantity, price,
currency, amount, entry type) — not a full replica of trade-processor's or
ledger-service's schema. Reconciliation should not become a second source of
truth; it is a checker.

### Break taxonomy
`ReconciliationEngine.evaluate()` checks for eight distinct break types:
`MISSING_IN_LEDGER`, `MISSING_DEBIT`, `MISSING_CREDIT`, `DUPLICATE_LEDGER_ENTRY`,
`UNBALANCED_DOUBLE_ENTRY`, `AMOUNT_MISMATCH`, `CURRENCY_MISMATCH`, each with a
severity (`CRITICAL`/`HIGH`/`MEDIUM`). This maps directly to how real back-office
reconciliation teams categorize breaks for triage.

### Self-healing re-check
Every scheduler run re-checks both `PENDING` (newly arrived) and `BROKEN`
(previously failed) trade facts. If a previously broken trade resolves on
re-check — for example, the ledger entry arrived late but is now present — the
open break is automatically marked `RESOLVED` and the trade fact moves back to
`RECONCILED`. Breaks are not manually closed; they close themselves once the
underlying data is consistent.

### Grace window before first check
A 30-second grace window (`rtrs.reconciliation.grace-window-seconds`) delays
the first check on a newly arrived trade fact, mirroring the same timing
philosophy as `ChoreographyTimeoutHandler`'s 30-second approval timeout in
trade-processor — give the asynchronous Kafka pipeline a realistic window to
settle before treating a missing counterpart as an actual break.

### Run audit trail
`ReconciliationRun` records every scheduler execution — start time, completion
time, duration, trades checked, breaks found, breaks resolved, and failure
reason if the run itself errored. This gives a queryable history of
reconciliation health over time, independent of the breaks themselves.

## Why No Outbox
Reconciliation-service produces no downstream event. Its output is internal
state (`reconciliation_breaks`, `reconciliation_runs`) exposed via a paginated
REST API for operational/compliance review, not something another service
consumes via Kafka. There is nothing to publish, so there is no dual-write
problem, so the outbox pattern does not apply.

## Why No Avro
By the same reasoning as ADR-011: Avro direct-publish exists for services that
evaluate and emit a result other services act on synchronously in the
choreography (risk-engine, aml-engine). Reconciliation-service is not part of
the trade approval choreography — it runs independently, on its own schedule,
well after a trade has already executed and settled into the ledger. It only
ever consumes, using `StringDeserializer` against the JSON payloads published
by the outbox-based services it watches.

## Consequences
- Reconciliation-service's `KafkaConfig` only ever needs a consumer factory —
  no `KafkaTemplate`, no Schema Registry dependency at all.
- If a future service needs to react to reconciliation breaks in real time
  (e.g. an alerting service), that would require adding a `breaks.raised.v1`
  Kafka topic and revisiting this decision — not a change to make casually,
  since it would turn reconciliation from a pure sink into a source.