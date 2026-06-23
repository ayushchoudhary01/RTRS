# 06 — settlement-service

## What It Does

Implements T+2 settlement: takes every executed trade, waits two business
days, nets same-account-same-instrument obligations together, validates
that both legs of Delivery-vs-Payment can actually be honored, and either
settles or escalates through a retry cycle. The most architecturally
complex service in RTRS — it's the only one using Spring Batch, the only
one modelling multilateral netting, and the only one with a genuine (if
minimal) security-position model.

| | |
|---|---|
| Port | 8089 |
| Database | settlement_db (5437) |
| Publishes | `settlement.settled.v1` (JSON, via outbox), `settlement.failed.v1` / `settlement.escalated.v1` (JSON, direct) |
| Consumes | `trade.executed.v1` (JSON) |

## Why It Exists

Trade execution and settlement are not the same event in real markets — a
trade executing means both parties have agreed to the trade; settlement is
the actual exchange of securities for cash, which by regulation happens on a
delay (T+2 for most equities). This service models that gap honestly,
including the fact that a trade can execute cleanly and still fail to settle
if the account doesn't actually have the cash or securities when settlement
day arrives.

## Lifecycle

```
PENDING → CONFIRMED → CLEARING → SETTLED
                          ↘
                          FAILED → RETRYING → FAILED → ESCALATED
```

- **PENDING** — instruction created the moment `trade.executed.v1` arrives,
  settlement date set to trade date + `settlement-cycle-days` (default 2).
- **CONFIRMED** — deliberately redefined as "prerequisites verified before
  entering clearing," not "counterparty affirmation" — RTRS has no
  counterparty model to affirm against, so the state means something the
  system can actually check rather than something borrowed from real
  settlement systems that doesn't apply here.
- **CLEARING** — instruction has entered an active `SettlementRun` and been
  grouped for netting.
- **SETTLED** — DVP check passed.
- **FAILED → RETRYING → FAILED → ESCALATED** — a failed DVP check is not
  immediately terminal. The instruction cycles through `FAILED`/`RETRYING`
  up to `max-retry-attempts` (default 3) before `ESCALATED`, which means
  manual intervention. A flat `FAILED` state alone would be ambiguous
  between "currently retrying" and "given up entirely" — the explicit
  `RETRYING` state removes that ambiguity.

## Multilateral Netting, Not Fake Bilateral CCP Simulation

Real Central Counterparty netting nets obligations *between two specific
counterparties*. RTRS has no counterparty model — every trade is between an
account and an abstract market. Building a fake counterparty model just to
claim "CCP-style netting" would be dishonest complexity.

Instead, `NettedObligation` nets **multilaterally** per
`(accountId, instrumentId, settlementDate)`: if an account has several BUY
and SELL trades for the same instrument settling the same day, they collapse
into a single net obligation (e.g. BUY 100 + BUY 40 + SELL 20 = net BUY 120)
before DVP validation runs. This is exactly how real clearing houses net at
the multilateral level (NSE/NSCCL in India works this way), and it's an
honest match for what the system can actually model.

## DVP: Genuine, Not Cash-Only Pretending To Be DVP

Delivery-vs-Payment means both legs — securities *and* cash — must be
available, or neither side settles. Ledger-service only tracks cash; nothing
else in RTRS tracked security quantities until this service introduced
`PositionBalance` (`accountId`, `instrumentId`, `quantity`), updated the
moment a trade *executes* (not when it settles — exposure exists from
execution onward, matching how real markets work).

**`DvpValidator.validate()`** checks both legs independently:
- **SELL obligations** need sufficient quantity in `PositionBalance`.
- **BUY obligations** need sufficient cash, checked via a REST call to
  ledger-service's balance endpoint. The required amount is the **sum of
  the actual instructions' notional** that were netted into the obligation
  — not a placeholder or proxy price. An earlier draft used a hardcoded
  `BigDecimal.ONE` as a stand-in price; this was caught and replaced before
  being built, specifically because a financial validation check with a
  fake number in it is worse than not having the check at all.

## Why Spring Batch, Not a Plain `@Scheduled` Method

A naive scheduled method calling settlement logic directly would work, but
loses everything that makes batch processing operationally real:
`JobRepository`-backed execution history, chunked processing, fault
tolerance with skip limits, and restartability. `EodSettlementJob` is built
on genuine Spring Batch primitives (`StepBuilder`, `ItemProcessor`,
`ItemWriter`) specifically for the step that's actually fault-prone — DVP
validation, which calls out to another service over REST and can genuinely
fail mid-run. The fast, single-pass steps (confirming due instructions,
netting them) happen directly in `SettlementLifecycleService` before the
batch job is ever launched; only the settlement-attempt step runs inside
Spring Batch's fault-tolerant chunk processing.

## Key Classes

**`SettlementInstruction`** — the per-trade record, carrying the lifecycle
state above plus a `nettedObligationId` once it's been grouped.

**`NettedObligation`** — the net position per account/instrument/date, with
`netDirection` (BUY/SELL) and `netQuantity` derived by summing signed
quantities across the group.

**`ClearingService.clearForRun()`** — groups due instructions by
`(accountId, instrumentId, settlementDate)`, creates one `NettedObligation`
per group, and links every instruction in that group back to it.

**`SettlementLifecycleService`** — `confirmDueInstructions()` (step 1),
`clearConfirmedInstructions()` (step 2, delegates to `ClearingService`), and
`settleObligation()` (step 3, called from inside the batch job — runs DVP,
on pass marks settled and writes the outbox event, on fail delegates to
`FailedSettlementHandler`).

**`FailedSettlementHandler`** — increments the retry count, decides
`RETRYING` vs `ESCALATED` against `max-retry-attempts`, and publishes
`settlement.failed.v1` / `settlement.escalated.v1` **directly**, not via
outbox.

**`SettlementRun`** — one row per EOD batch execution, tracking
instructions-processed/succeeded/failed counts and timing. Mirrors
`ReconciliationRun` in reconciliation-service — every `SettlementInstruction`
and `NettedObligation` carries the `settlementRunId` of the run that
processed it, giving full traceability from any instruction back to the
exact batch execution.

**`SettlementScheduler`** — the daily entry point (`eod-cron`, default 6PM),
which runs steps 1–2 directly and then launches the Spring Batch job via
`JobOperator` (not the deprecated `JobLauncher`) for step 3.

## Settled vs Failed: Different Publish Guarantees, Same Reasoning as Elsewhere

`settlement.settled.v1` goes through the **outbox** — it's correctness-
critical, the same way `trade.executed.v1` is. `settlement.failed.v1` and
`settlement.escalated.v1` publish **directly**, because the actual source of
truth for a failure is the instruction's persisted `FAILED`/`ESCALATED`
status in the database; if the Kafka notification itself fails to send,
nothing is lost — the next scheduler run picks the instruction up again
based on its DB state regardless. This mirrors the outbox-vs-direct
reasoning documented for risk/AML in
`docs/architecture-decision-records/011-avro-serialization-boundary.md`,
applied to a same-format (JSON) case rather than a serialization-format
case.
