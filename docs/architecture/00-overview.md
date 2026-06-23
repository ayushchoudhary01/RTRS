# RTRS — System Design Overview

## What This Is

RTRS (Real-Time Trade & Risk System) is a BFSI-grade microservices platform
that processes financial trades end to end: submission, risk evaluation,
AML screening, execution, ledger posting, settlement, and reconciliation.
Seven services, each independently deployable with its own PostgreSQL
database, communicating through Kafka.

This is not a toy CRUD demo. It implements patterns used in real trading and
post-trade systems: the transactional outbox pattern, choreography sagas,
double-entry bookkeeping with tamper-evident hash chains, multilateral
netting, DVP settlement validation, and CQRS where the complexity justifies
it.

## The Seven Services

| Service | Port | Database | Role |
|---|---|---|---|
| trade-ingestion-service | 8086 | trade_db (5433) | Entry point — accepts trades via REST |
| trade-processor-service | 8087 | processor_db (5441) | Choreography orchestrator |
| risk-engine-service | 8083 | risk_db (5435) | Risk rule evaluation |
| aml-engine-service | 8084 | aml_db (5436) | AML rule evaluation |
| ledger-service | 8085 | ledger_db (5434) | Double-entry bookkeeping |
| settlement-service | 8089 | settlement_db (5437) | T+2 settlement, netting, DVP |
| reconciliation-service | 8088 | recon_db (5439) | Cross-checks ledger vs trade records |

Three more services exist as empty skeletons only (api-gateway,
audit-service, market-data-service) — see `docs/future/` for what they would
do.

## The Trade Lifecycle, In One Picture

```
POST /api/v1/trades
        ↓
trade-ingestion-service
   (validates, writes Trade + outbox row atomically)
        ↓ Kafka: trade.submitted.v1 (JSON)
        ├──────────────────────┬──────────────────────┐
        ↓                      ↓                       ↓
trade-processor          risk-engine             aml-engine
(creates approval         (evaluates rules,       (evaluates rules,
 state, waits)             publishes Avro)         publishes Avro)
        ↑                      ↓                       ↓
        └── risk.approved.v1 ──┘                       │
        └── aml.cleared.v1 ────────────────────────────┘
        ↓
   both cleared?
        ↓ yes                              ↓ no (30s timeout)
   EXECUTE                            REJECT (compensating action)
        ↓
   trade.executed.v1 (JSON, via outbox)
        ↓
   ┌────────────┬──────────────────┐
   ↓            ↓                  ↓
ledger-service  settlement-service reconciliation-service
(double-entry,  (creates PENDING   (cross-checks ledger
 hash chain)     instruction,       entries against trade
                 T+2 settlement     records, every minute)
                 date)
```

## Two Serialization Strategies, By Design

This is the single most important architectural fact to understand before
reading any service doc:

**Outbox-based services** (trade-ingestion, trade-processor, ledger,
settlement) write business data and a Kafka event in one local transaction,
then a `@Scheduled` poller relays the event as a **JSON string** via
`StringSerializer`. This guarantees the DB write and the Kafka publish never
get out of sync — if the service crashes after the DB commit but before the
Kafka send, the event is still sitting in the outbox table to be retried.

**Evaluation engines** (risk-engine, aml-engine) have no business state to
write atomically — they consume an event, run a stateless rule chain, and
publish a result. They publish directly using **Avro** via
`KafkaAvroSerializer` against Confluent Schema Registry, because there's no
dual-write problem to solve and Avro's schema enforcement is worth having
for the events other services build real business logic on top of
(`RiskApprovedEvent`, `AmlFlaggedEvent`, etc.).

**reconciliation-service** only ever consumes (both `trade.executed.v1` and
`ledger.entry.created.v1`), via `StringDeserializer`, since both are
outbox-published JSON. It never publishes anything — it's a pure sink, not
part of the choreography.

This split is documented formally in
`docs/architecture-decision-records/011-avro-serialization-boundary.md`.

## Three Real Trade Outcomes (Verified)

The system has been tested end-to-end with three distinct outcomes, each
traced through every service's logs:

1. **Clean execution** — risk approves, AML clears, trade executes, ledger
   posts a balanced double-entry, settlement creates a T+2 instruction,
   reconciliation matches it with zero breaks.
2. **AML-only rejection** — risk approves but AML flags (e.g. `ROUND_AMOUNT`
   rule). The trade never reaches execution; the 30-second choreography
   timeout fires and rejects it.
3. **Dual rejection** — both risk (`POSITION_LIMIT`) and AML
   (`HIGH_VALUE_TRANSACTION`) independently flag the same trade. Neither
   service knows what the other concluded — they evaluate in parallel and
   reach the same correct verdict from different rules.

See `docs/system-design/08-happy-path-trade-lifecycle.md` and
`09-rejection-paths.md` for the full traced walkthroughs.

## Known Current Issues

Two import-level bugs exist in the current codebase, found during a full
line-by-line review:

- `ledger-service/kafka/TradeExecutedConsumer.java` imports
  `tools.jackson.databind` (Jackson 3.x namespace) instead of
  `com.fasterxml.jackson.databind` (the Jackson 2.x namespace every other
  service uses). This will fail to compile against the actual classpath.
- `settlement-service/api/SettlementController.java` imports
  `org.apache.kafka.common.errors.ResourceNotFoundException` instead of the
  service's own `com.rtrs.settlementservice.exception.ResourceNotFoundException`.
  It compiles (both are valid `RuntimeException` subtypes) but the custom
  `GlobalExceptionHandler` won't catch Kafka's version, so a 404 case falls
  through to a generic 500.

Both are documented precisely in
`docs/troubleshooting/009-ambiguous-imports.md` — the fix pattern is known
and correct, it simply wasn't carried into these two specific files.

## How to Read the Rest of These Docs

- **Per-service files (`01` through `07`)** — what each service does, why it
  exists, its key classes, and what it consumes/produces on Kafka.
- **`08-happy-path-trade-lifecycle.md`** — one trade, traced through all
  seven services, with real log evidence.
- **`09-rejection-paths.md`** — what happens when risk or AML says no.
- **`10-shared-libraries.md`** — `rtrs-common`, `rtrs-events`, `rtrs-security`,
  and what each service actually uses from them.

For *why* a given design decision was made, see `docs/architecture-decision-records/`.
For bugs found and fixed during development, see `docs/troubleshooting/`.
For what's deliberately deferred, see `docs/future/`.