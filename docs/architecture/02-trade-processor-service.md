# 02 — trade-processor-service

## What It Does

The choreography orchestrator. It doesn't evaluate risk or AML itself — it
listens for the verdicts from risk-engine and aml-engine, tracks which trades
have cleared both, and is the one service that actually decides a trade is
ready to execute. It also owns the timeout/rejection path when a trade never
gets a clean verdict from both sides.

| | |
|---|---|
| Port | 8087 |
| Database | processor_db (5441) |
| Publishes | `trade.executed.v1`, `trade.rejected.v1` (JSON, via outbox) |
| Consumes | `trade.submitted.v1` (JSON), `risk.approved.v1` / `aml.cleared.v1` (Avro) |

## Why It Exists

Risk and AML evaluation happen independently and in parallel — neither
service knows about the other, and neither should have to. Something has to
sit in the middle, remember "risk said yes" and "AML said yes" as two
separate facts about the same trade, and act only once both are true. That's
this service's entire reason for existing: it's the aggregation point for a
**choreography saga** — no central orchestrator telling risk and AML what to
do, just independent services reacting to events, with this service watching
for the combination that means "go."

## Key Classes

### Choreography

**`TradeApprovalState`** — one row per trade, tracking `riskCleared` and
`amlCleared` booleans independently, plus a derived `status` string
(`PENDING` → `RISK_CLEARED` / `AML_CLEARED` → `APPROVED`, or `REJECTED` /
`TIMEOUT`). Also carries `quantity`, `limitPrice`, and `currency` — these
were added after the fact (see
`docs/architecture-decision-records` and the V2 migration) specifically so
that downstream services like ledger could be given a complete
`trade.executed.v1` payload without trade-processor needing to call back
into trade-ingestion.

**`TradeApprovalAggregator`** — the only class that mutates
`TradeApprovalState`. `markRiskCleared()` and `markAmlCleared()` both:
1. Take a **pessimistic lock** (`SELECT ... FOR UPDATE`) on the row.
2. If the row doesn't exist yet, return `false` instead of throwing — this
   handles the case where risk or AML's verdict arrives *before*
   `initiate()` has run, which happens because evaluation engines are
   stateless and fast while this service has to do a DB write first. The
   calling consumer (see below) re-throws to trigger a Kafka redelivery, so
   the event isn't lost, just retried until the row exists.
3. If the trade is already `APPROVED`, return `false` — idempotency guard
   against duplicate events re-triggering execution.
4. Otherwise mark the relevant flag and return whether *both* are now true.

The pessimistic lock matters here for the same reason it matters in
ledger-service's hash chain: two consumer threads (risk's consumer thread and
AML's consumer thread) could otherwise both read `bothCleared = false`,
both think they're the one to flip it to `true`, and both try to trigger
execution.

**`ChoreographyTimeoutHandler`** — a `@Scheduled` job that finds any
`TradeApprovalState` still `PENDING`/`RISK_CLEARED`/`AML_CLEARED` after 30
seconds and rejects it via `CompensatingActionService`. This is the only
mechanism that handles a risk or AML **rejection** — there is no
`RiskBreachedConsumer` or `AmlFlaggedConsumer` in this service. When risk
publishes `risk.breached.v1` or AML publishes `aml.flagged.v1`, nothing here
listens for it directly; the trade simply never gets marked cleared on that
side, and 30 seconds later the timeout handler notices and rejects it. This
is a deliberate simplification, not an oversight — it means rejection always
takes up to 30 seconds even when the rejection reason is known instantly, but
it also means there's only one rejection code path to get right instead of
three.

**`CompensatingActionService`** — writes the `trade.rejected.v1` outbox
event when the timeout handler gives up on a trade.

### State Machine

**`TradeStateMachineConfig`** — a Spring State Machine definition
(`SUBMITTED → RISK_CLEARED → AML_CLEARED → EXECUTING → EXECUTED`, with
`REJECTED` as a terminal state from several points). Configured with
`@EnableStateMachineFactory`, not `@EnableStateMachine` — the factory variant
is what allows a **separate state machine instance per trade**, keyed by
`tradeId.toString()`. The plain `@EnableStateMachine` annotation creates one
shared singleton instance, which would corrupt state under any concurrency —
two trades executing at the same time would be racing on the same machine.
See `docs/troubleshooting` for this fix's history.

**`TradeExecutionService`** — when the aggregator says both sides have
cleared, this is what actually runs. It fetches the trade's
`TradeApprovalState` (for the `quantity`/`limitPrice`/`currency` fields it
needs), drives the per-trade state machine to `EXECUTED`, and writes the
`trade.executed.v1` outbox event with the full payload — `tradeId`,
`accountId`, `instrumentId`, `quantity`, `limitPrice`, `currency`,
`executionStatus`, `executedAt`. This enriched payload is what lets ledger,
settlement, and reconciliation all work from the same event without any of
them needing to call back to trade-ingestion for the original trade details.

### Kafka

**`TradeSubmittedConsumer`** — parses the JSON outbox payload, calls
`TradeApprovalAggregator.initiate()`.

**`RiskApprovedConsumer`** / **`AmlClearedConsumer`** — these consume
**Avro**, not JSON, using a dedicated `avroKafkaListenerContainerFactory`
(see Shared Pattern note below). Each extracts the `tradeId`, calls the
matching `markXCleared()` method, and if it returns `true`, calls
`TradeExecutionService.execute()`.

**`DeadLetterQueueConsumer`** / **`DlqAlertPublisher`** — listens on
`trade.submitted.v1.DLT`. A `DefaultErrorHandler` backed by a
`DeadLetterPublishingRecoverer`, configured on this service's main consumer
factory, retries a failing message 3 times with a 1-second backoff and then
routes it to the `.DLT` topic instead of retrying forever. This consumer is
what actually reads from that topic and logs an alert — without it, the DLT
topic existed but had nothing listening, a dead end with no visibility. This
was a real gap found and closed during development (see
`docs/troubleshooting`).

## A Note on Avro Inside a JSON-Outbox Service

This is the one service that has to speak both serialization formats. It
publishes JSON (because it uses the outbox pattern for `trade.executed.v1`
and `trade.rejected.v1`) but it must **consume** Avro (because risk-engine
and aml-engine publish their verdicts directly via `KafkaAvroSerializer`).
That's why `KafkaConfig` here defines two separate
`ConcurrentKafkaListenerContainerFactory` beans — one with
`StringDeserializer` for `trade.submitted.v1`, and one with
`KafkaAvroDeserializer` (with `SPECIFIC_AVRO_READER_CONFIG=true`) for
`risk.approved.v1` / `aml.cleared.v1`. Mixing these up — pointing a String
consumer at an Avro topic — produces a binary parse error that looks like a
JSON syntax error (`CTRL-CHAR` exception), which is exactly what happened the
first time this was wired up incorrectly. See
`docs/troubleshooting/006-avro-serialization-mismatch.md`.

## The Race This Service Has To Handle

Risk-engine and AML-engine are both stateless and fast — they evaluate and
publish in milliseconds. This service has to do a database write
(`TradeApprovalAggregator.initiate()`) before it has anywhere to record a
verdict. Under real load, it's entirely possible for `RiskApprovedConsumer`
to receive a verdict for a trade whose `TradeApprovalState` row doesn't exist
yet. The fix isn't to make trade-processor faster — it's to make the
consumers tolerant of arriving early: `markRiskCleared()` /
`markAmlCleared()` return `false` gracefully instead of throwing when the row
is missing, and the consumer re-throws as a generic exception specifically
so Kafka's normal retry mechanism redelivers the message a moment later, by
which point the row almost always exists.