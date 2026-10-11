# 005 — Choreography Saga with a Locked Approval Row

## Status
Accepted. See "What Actually Tracks State" below for the component that holds the
approval lifecycle.

## Decision
Cross-service communication is purely choreography-based over Kafka. No central
orchestrator tells risk-engine and aml-engine what to do — each reacts to
`trade.submitted.v1` independently and publishes its own verdict.

Within `trade-processor-service`, the approval lifecycle is tracked by a single
pessimistically-locked JPA row (`TradeApprovalState`), not by a running workflow
engine.

## Context
A trade must be cleared by both the Risk Engine and the AML Engine before it can
execute. Cross-service coordination has to be loosely coupled and fault-tolerant.
But *something* has to remember "risk said yes" and "AML said yes" as two separate
facts about the same trade, and act only once both are true — the two verdicts
arrive independently, in either order, on two different Kafka consumer threads.

## Cross-Service Layer — Choreography via Kafka
Services communicate exclusively through Kafka events. No service knows the
internal logic of another.

```
trade.submitted.v1
      ↓ (consumed independently)
trade-processor  → creates the approval row
risk-engine      → emits risk.approved.v1 OR risk.breached.v1
aml-engine       → emits aml.cleared.v1 OR aml.flagged.v1
```

### Why choreography here
- No single point of failure — risk-engine and aml-engine operate independently
- Loose coupling — neither evaluation engine knows trade-processor exists, and
  neither exposes a synchronous API for an orchestrator to call
- Extensible — adding a third evaluation service means adding one more consumer of
  `trade.submitted.v1`, with no change to any existing service
- Kafka provides durability — a service that goes down resumes from its last
  committed offset on recovery

The cost, stated plainly: a single trade's path is harder to trace by reading one
service's code than it would be under orchestration. That is the trade-off, and it
is the reason `reconciliation-service` exists.

## What Actually Tracks State

`TradeApprovalState` — one row per trade, holding:

- `riskCleared` and `amlCleared` as **two independent booleans**
- a derived `status` string (`PENDING` → `RISK_CLEARED` / `AML_CLEARED` →
  `APPROVED`, or `REJECTED` / `TIMEOUT`)
- `quantity`, `limitPrice`, `currency`, so `trade.executed.v1` can carry a complete
  payload without calling back into trade-ingestion
- `createdAt`, which the timeout scan uses

`TradeApprovalAggregator` is the only class that mutates it. Both
`markRiskCleared()` and `markAmlCleared()`:

1. Take a pessimistic lock (`SELECT ... FOR UPDATE`) on the row
2. Return `false` if the row does not exist yet — the verdict arrived before
   `initiate()` committed, and the calling consumer re-throws so Kafka redelivers
   (see `docs/troubleshooting/007-aml-risk-race-condition.md`)
3. Return `false` if the trade is already in a terminal state — `APPROVED` guards
   against duplicate execution, `REJECTED`/`TIMEOUT` against a late verdict
   resurrecting a trade that has already been rejected
4. Otherwise set the flag and return whether *both* are now true

The lock is what makes this safe rather than a race: risk's consumer thread and
AML's consumer thread can both reach this row at nearly the same instant, and
without serialization both could read `bothCleared = false`, both set their own
flag, and both conclude they should trigger execution.

### The lifecycle it implements

```
PENDING ──risk cleared──→ RISK_CLEARED ──┐
   │                                      ├──→ APPROVED → trade.executed.v1
   └───aml cleared───→ AML_CLEARED ──────┘

any of the above, after 30s without both clearances:
   └──→ TIMEOUT → trade.rejected.v1 (compensating action)
```

Rejection is handled by **exactly one** mechanism: `ChoreographyTimeoutHandler`, a
`@Scheduled` job running every 10 seconds that finds rows still awaiting a
clearance after 30 seconds and rejects them via `CompensatingActionService`.

There is no `RiskBreachedConsumer` and no `AmlFlaggedConsumer`. When risk publishes
`risk.breached.v1` or AML publishes `aml.flagged.v1`, nothing in trade-processor
listens — the corresponding flag simply never gets set, and the timeout notices 30
seconds later. This is a deliberate simplification: one rejection code path instead
of three. The honest cost is that every rejection takes the full 30 seconds even
when the reason was known instantly. See
`docs/architecture/09-rejection-paths.md` for two traced examples.

## Why Two Flags, Not a Single Status Column

The obvious alternative is one `status` column walked through a fixed sequence of
values, driven either by a workflow library or a hand-rolled transition table. The
row such an engine would sit on top of already holds everything it would:

- **One status column holds one fact. This workflow needs two independent ones.**
  "Risk cleared, AML pending" and "AML cleared, risk pending" are both legitimate
  and must be distinguishable, and either can come first. Two booleans express that
  directly; a single enum has to encode the cross-product.
- **The aggregation needs a locked database row anyway.** The pessimistic lock that
  serializes the two consumer threads has to live on a row in this service's
  database. Once that row exists, it already *is* the state.
- **The row carries more than state.** The quantity/price/currency fields and
  `createdAt` are needed by `TradeExecutionService` and the timeout scan, and would
  sit outside any engine's model.
- **Two stores for one trade's state would be worse.** An engine with its own
  persistence, running alongside `TradeApprovalState`, gives one trade two durable
  state records that must agree. Redundant state in a financial system is a
  liability, not a feature.

The concurrency requirement is the decisive one. Two consumer threads can update
the same trade's clearances at the same moment, so correctness depends on a
pessimistic lock held across the whole read-modify-write —
`findByTradeIdForUpdate()`. That lock has to be on a database row. Once the row
exists and is locked, anything else holding the same state adds a consistency
problem without solving one.

## Why Not Temporal
Temporal is the production-grade choice for durable workflow orchestration and is
what banks actually use for this. It was not used here because it requires
dedicated infrastructure (a Temporal cluster) and significant operational overhead
for a workflow this small — two boolean flags and a timeout. If this system's
workflow grew to many steps with real compensation chains, Temporal would be the
correct answer, not a hand-rolled approval row.

## Alternatives Rejected
- **Central saga orchestrator** — single point of failure, and it would require
  risk-engine and aml-engine to expose synchronous evaluation endpoints they do not
  have
- **A single status column driven by a transition table** — cannot represent two
  independent clearances without encoding their cross-product, and duplicates state
  the aggregation already has to hold under a lock
- **Temporal** — correct at larger scale, disproportionate infrastructure here
- **Optimistic locking on the approval row** — a version-and-retry approach would
  work for the flags, but the same lock also serializes ledger-side hash chaining
  elsewhere in the system, and using one pattern consistently was preferred

## Consequences
- Rejection latency is always ~30 seconds, even for an instantly-known breach
- The timeout handler is the *only* mechanism that revisits an approval row — if it
  stopped firing, rejected trades would sit awaiting clearance indefinitely
- Eventual consistency across services is accepted by design, which is why
  `reconciliation-service` independently verifies that trade records and ledger
  entries agree (see ADR 012)
