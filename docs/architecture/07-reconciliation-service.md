# 07 — reconciliation-service

## What It Does

Independently cross-checks ledger entries against trade execution records to
detect breaks — discrepancies between what the trade pipeline says happened
and what the ledger actually recorded. Runs on its own schedule, separate
from the real-time trade flow, the way a real back-office reconciliation
team operates.

| | |
|---|---|
| Port | 8088 |
| Database | recon_db (5439) |
| Publishes | nothing |
| Consumes | `trade.executed.v1`, `ledger.entry.created.v1` (both JSON) |

## Why It Exists

The choreography saga across trade-processor, risk-engine, aml-engine, and
ledger-service is asynchronous and eventually consistent by design — that's
the trade-off for not having a single orchestrator. Reconciliation exists
precisely because "eventually consistent" needs a service whose whole job
is checking that the "eventually" part actually happened. Without it, a
silently dropped Kafka message or a partial write somewhere downstream could
go unnoticed indefinitely.

## Why It's a Pure Sink — No Outbox, No Avro

This is the only service in RTRS that produces no Kafka event for anything
else to consume. Its output is internal state (`reconciliation_breaks`,
`reconciliation_runs`) exposed via a read-only REST API for operational
review, not a signal another service reacts to. With nothing to publish,
neither the outbox pattern (which exists to make a write-plus-publish
atomic) nor direct Avro publish (which exists for services that emit a
result other services act on) applies — there's no publish step in the
first place. `KafkaConfig` here defines only a consumer factory, with no
`KafkaTemplate` bean and no Schema Registry dependency at all. Documented
formally in
`docs/architecture-decision-records/012-reconciliation-service-design.md`.

## Key Classes

**`TradeFact`** / **`LedgerFact`** — minimal projections of what each side
of the comparison needs, not full copies of trade-processor's or
ledger-service's schemas. `TradeFact` records quantity, price, currency.
`LedgerFact` records one row per `(journalId, entryType)` — so a single
trade typically produces one `TradeFact` and two `LedgerFact`s (the DEBIT
and CREDIT). Idempotency: `existsById(tradeId)` for trade facts,
`existsByJournalIdAndEntryType()` for ledger facts — both consumers skip
silently if the fact already exists, which matters because both topics can
redeliver under Kafka's at-least-once guarantee.

**`TradeExecutedConsumer`** / **`LedgerEntryCreatedConsumer`** — parse the
JSON payload, persist the corresponding fact. Nothing else happens at
ingestion time; matching is deferred entirely to the scheduled engine run.

**`ReconciliationEngine.evaluate()`** — the actual matching logic, checking
for eight distinct break types:

| Break Type | Severity | Meaning |
|---|---|---|
| `MISSING_IN_LEDGER` | CRITICAL | trade executed, no ledger entries arrived at all |
| `MISSING_DEBIT` | CRITICAL | only a CREDIT entry exists, no DEBIT |
| `MISSING_CREDIT` | CRITICAL | only a DEBIT entry exists, no CREDIT |
| `DUPLICATE_LEDGER_ENTRY` | HIGH | more than one entry of the same type for one journal |
| `UNBALANCED_DOUBLE_ENTRY` | CRITICAL | DEBIT and CREDIT amounts don't match |
| `AMOUNT_MISMATCH` | HIGH | ledger amount doesn't match trade notional |
| `CURRENCY_MISMATCH` | MEDIUM | currency differs between trade and ledger |

This taxonomy maps directly onto how real back-office reconciliation teams
categorize breaks for triage — severity isn't arbitrary, it reflects how
urgently each break type needs human attention.

**`ReconciliationScheduler`** — runs every minute (configurable), and on
each run:
1. Re-checks every `BROKEN` fact from previous runs first — if the
   underlying data has since arrived (e.g. a late ledger entry), the break
   self-heals and is marked `RESOLVED` automatically. Breaks are never
   manually closed; they close themselves once the data is consistent.
2. Checks every `PENDING` fact for the first time, but only once a
   30-second grace window has passed since it arrived — the same timing
   philosophy as `ChoreographyTimeoutHandler`'s 30-second approval timeout
   in trade-processor, giving the asynchronous pipeline a realistic window
   to settle before treating a missing counterpart as a genuine break.

**`ReconciliationRun`** — one row per scheduler execution: start/completion
time, status, and counts of trades checked / breaks found / breaks resolved.
Gives a queryable history of reconciliation health independent of the
breaks themselves — this is the pattern `SettlementRun` in settlement-
service later copied.

**`ReconciliationBreak`** — the persisted record of a detected discrepancy,
with its type, severity, and resolution status.
