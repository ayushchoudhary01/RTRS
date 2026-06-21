# 013 — Settlement Service: Lifecycle, Netting, and DVP Design

## Status
Accepted

## Decision
`settlement-service` implements T+2 settlement with a multi-state lifecycle,
multilateral netting per account/instrument/settlement-date, and a Delivery
vs Payment (DVP) check backed by genuine position tracking rather than a
cash-only approximation.

## Lifecycle States
```
PENDING → CONFIRMED → CLEARING → SETTLED
                          ↘
                          FAILED → RETRYING → FAILED → ESCALATED
```

- **PENDING** — instruction created from `trade.executed.v1`, settlement date
  calculated as trade date + `settlement-cycle-days` (default 2).
- **CONFIRMED** — explicitly redefined as "all prerequisites verified before
  entering clearing," not "counterparty affirmation" (which RTRS has no model
  for, since trades aren't modelled bilaterally). This keeps the state
  meaningful without inventing a counterparty concept the system doesn't have.
- **CLEARING** — instruction has entered an active `SettlementRun` and been
  grouped for netting.
- **SETTLED** — DVP check passed, cash and securities considered exchanged.
- **FAILED → RETRYING → FAILED → ESCALATED** — a failed DVP check is not
  treated as terminal on the first attempt. The instruction cycles through
  `FAILED`/`RETRYING` up to `max-retry-attempts` (default 3) before being
  marked `ESCALATED`, which requires manual intervention. `FAILED` alone would
  ambiguously mean both "currently retrying" and "exhausted all retries" —
  the explicit `RETRYING` state removes that ambiguity.

## Netting: Multilateral, Not Fake Bilateral
Real CCP (Central Counterparty) netting nets obligations *between
counterparties* — if Account A owes Account B and B owes A, only the net
difference settles. RTRS has no counterparty model; every trade is between an
account and the market abstractly. Building a fake counterparty model just to
claim "CCP netting" would be dishonest complexity.

Instead, `NettedObligation` implements multilateral netting per
`(accountId, instrumentId, settlementDate)` — if an account has multiple BUY
and SELL trades for the same instrument settling on the same date, they're
netted into a single obligation before DVP validation. This is exactly how
real clearing houses (e.g. NSE/NSCCL in India) net at the multilateral level,
and it's honest about what RTRS can and cannot model.

## DVP: Genuine, Not Cash-Only Pretending to Be DVP
Ledger-service only tracks cash balance — there is no position/security-
quantity tracking anywhere else in RTRS. A naive DVP implementation would
check cash only and call it "DVP," which misrepresents what DVP means (it
requires *both* legs — securities AND cash — to be available).

To make this honest, settlement-service maintains its own minimal
`PositionBalance` table (`accountId`, `instrumentId`, `quantity`), updated at
trade execution time (not settlement time, matching how real exposure exists
pre-settlement) from `trade.executed.v1`. `DvpValidator` checks:
- **SELL obligations** — sufficient security quantity in `PositionBalance`
- **BUY obligations** — sufficient cash balance, queried from ledger-service
  via REST, using the sum of actual instruction notional amounts (not a
  derived/proxy price — see Troubleshooting 010 era discussion: an earlier
  draft used a placeholder price and was rejected before being built)

This is a deliberately minimal position model — not a full custody service —
scoped exactly to what's needed for DVP to mean what it claims to mean.

## Why Spring Batch, Not @Scheduled
A naive `@Scheduled` method calling settlement logic directly would work, but
loses everything that makes EOD batch processing operationally real:
`JobRepository`-backed execution history, chunk-based processing with
configurable commit intervals, fault tolerance with skip limits, and
restartability if a run is interrupted mid-way. `EodSettlementJob` is built on
genuine Spring Batch primitives (`StepBuilder`, `ItemProcessor`, `ItemWriter`)
specifically so that settlement-service's EOD run has the same operational
characteristics a real bank's batch settlement engine would have. The
scheduler (`SettlementScheduler`) only handles the fast, single-pass
confirm-and-net steps; the genuinely fault-prone step (DVP validation against
an external service over REST) is the one wrapped in Spring Batch's
fault-tolerant chunk processing.

## SettlementRun: Audit Trail Per Batch Execution
Mirrors `ReconciliationRun` from reconciliation-service — every EOD execution
is tracked with start/completion time, status, and counts of processed/
succeeded/failed instructions. Every `SettlementInstruction` and
`NettedObligation` references its `settlementRunId`, giving full traceability
from any instruction back to the exact batch execution that processed it.

## Outbox vs Direct Publish — Same Reasoning as ADR-011 and ADR-012
`settlement.settled.v1` goes through the outbox (`OutboxPublisher`) because
it's correctness-critical — downstream consumers need a reliable guarantee
that a settlement actually happened. `settlement.failed.v1` and
`settlement.escalated.v1` are published directly via `SettlementEventProducer`
without an outbox, because the source of truth for a failure is the
instruction's persisted `FAILED`/`ESCALATED` status in the database, not the
Kafka notification. If the notification publish fails, nothing is lost — the
next scheduler run picks up the instruction again based on its DB status. The
notification is best-effort; the state transition is not.

## Consequences
- Settlement-service depends on ledger-service being reachable over REST for
  cash balance checks during DVP validation. If ledger-service is down,
  `DvpValidator` fails closed (treats the check as failed, not as passed),
  which is the correct conservative behaviour for a financial settlement
  check.
- The `PositionBalance` table in settlement-service is a second place
  (alongside ledger-service's cash balance) that must be consulted to fully
  understand an account's financial state. This is an accepted trade-off of
  not building a full custody/position microservice — documented as a future
  improvement if RTRS needs genuine multi-service position reconciliation.