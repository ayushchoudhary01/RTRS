# 03 — risk-engine-service

## What It Does

Evaluates every submitted trade against a chain of risk rules — position
limits, value-at-risk, concentration — and publishes a verdict directly to
Kafka. No outbox, no intermediate state machine. Consume, evaluate, publish.

| | |
|---|---|
| Port | 8083 |
| Database | risk_db (5435) |
| Publishes | `risk.approved.v1`, `risk.breached.v1` (Avro, direct) |
| Consumes | `trade.submitted.v1` (JSON) |

## Why It Exists

A bank does not let every trade through unchecked — position limits exist
to stop a single account or instrument from accumulating dangerous exposure,
VaR limits exist to bound how much a portfolio could lose under stress, and
concentration limits exist to stop over-exposure to a single name. This
service is the deterministic, rule-based gate that runs before AML and
before execution.

## Key Classes

**`RiskRule`** (interface) — `getRuleName()` and
`evaluate(RiskContext) → RiskRuleResult`. Strategy pattern: each rule is a
self-contained, independently testable unit.

**`PositionLimitRule`** — rejects if `notionalUsd` exceeds a configured
threshold (`rtrs.risk.position-limit-usd`, default $1,000,000).

**`VaRRule`** — a simplified Value-at-Risk check: notional × a configured
percentage, compared against 5% of an assumed $10M portfolio baseline. This
is explicitly a placeholder for real VaR, which would need historical price
volatility from a market-data service that doesn't exist yet — see
`docs/future/004-market-data-service.md`.

**`ConcentrationRule`** — rejects if a single instrument's notional exceeds
a configured percentage of the same assumed portfolio baseline.

**`RiskRuleChain`** — takes `List<RiskRule>` via constructor injection
(Spring auto-collects every `RiskRule` bean) and runs them in sequence,
stopping at the first breach. **Note:** unlike aml-engine's rules, none of
these three carry an `@Order` annotation — the chain runs in whatever order
Spring's component scan happens to discover the three rule beans, which is
not a guaranteed or intentional sequence. This is fine today because none of
the three rules depend on running before or after another, but it's worth
knowing this is implicit rather than explicit, in contrast to AML's rule
ordering.

**`RiskContext`** — a single object carrying everything a rule needs
(`tradeId`, `instrumentId`, `accountId`, `quantity`, `limitPrice`,
`notionalUsd`), passed to avoid parameter-list explosion as more rules are
added.

**`RiskEvaluationEntity`** — persisted record of every evaluation, `APPROVED`
or `BREACHED`, with the breaching rule and reason if applicable. Idempotency
is enforced via `existsByTradeId()` before any evaluation runs — a duplicate
`trade.submitted.v1` delivery (which can happen; Kafka delivery is at-least-
once) is detected and skipped rather than re-evaluated.

**`RiskEvaluationService`** — orchestrates: check idempotency, build
`RiskContext`, run the chain, persist the result, build and publish the
appropriate Avro event.

**`RiskEventProducer`** — wraps the Avro `KafkaTemplate`, exposing
`publishApproved()` / `publishBreached()`.

## Two Honestly-Flagged Placeholders

Two fields in the published events are not yet backed by real logic:

- `riskScore` is hardcoded to `0` in `RiskApprovedEvent`. A real risk score
  would need a scoring model; none exists yet.
- `thresholdValue` in `RiskBreachedEvent` is hardcoded to `0.0`. The actual
  threshold that was breached *is* known inside the rule that breached
  (e.g. `PositionLimitRule` knows its own configured limit), but the event
  schema asks for a single `thresholdValue` field and threading the
  specific rule's threshold back out to the event-building code wasn't done.

Both are deliberate, acknowledged gaps rather than silent bugs — they don't
affect the approve/breach decision itself, only the metadata carried on the
breach event.

## Why No Outbox

Risk-engine has no business state that needs to be written atomically with
a Kafka publish — there's no "trade record" it owns the way trade-ingestion
owns the `Trade` row. It reads an event, runs pure evaluation logic, and
emits a result. If the Kafka publish fails after the DB write, the
consequence is a missed verdict, which trade-processor's 30-second timeout
already handles by rejecting the trade — there's no silent data corruption
risk the way there would be if, say, a half-completed ledger entry existed
with no matching Kafka event. This is the same reasoning that justifies
direct Avro publish over the outbox pattern, documented formally in
`docs/architecture-decision-records/011-avro-serialization-boundary.md`.