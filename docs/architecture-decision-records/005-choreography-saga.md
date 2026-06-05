# 005 — Hybrid Saga: Choreography + Local Orchestration

## Status
Accepted

## Decision
RTRS uses a hybrid saga pattern. Cross-service communication is purely choreography-based over Kafka. Within the Trade Processor Service, a Spring State Machine acts as a local orchestrator managing the trade approval lifecycle.

## Context
A trade must be approved by both the Risk Engine and the AML Engine before execution. Cross-service coordination must be loosely coupled and fault-tolerant. But within the Trade Processor, the approval workflow is sequential, order-dependent, and requires precise state tracking — characteristics that benefit from local orchestration.

This mirrors the pattern used in production banking systems: choreography at the inter-service boundary, orchestration within a service boundary.

## Architecture

### Cross-Service Layer — Choreography via Kafka
Services communicate exclusively through Kafka events. No service knows the internal logic of another. Each reacts to events independently.

```
trade.submitted.v1
      ↓ (consumed independently)
Risk Engine     → emits risk.approved OR risk.breached
AML Engine      → emits aml.cleared OR aml.flagged
Audit Service   → writes immutable log entry
```

### Intra-Service Layer — Spring State Machine in Trade Processor
Once the Trade Processor receives approval signals, a Spring State Machine manages the local workflow:

```
SUBMITTED → RISK_CLEARED → AML_CLEARED → EXECUTING → EXECUTED
                 ↓ any failure at any step
             REJECTED → compensating event emitted
```

State is persisted in the Trade Processor's PostgreSQL database. A scheduled cleanup job auto-rejects trades that do not receive both approvals within 30 seconds.

## How It Works
1. Trade Ingestion publishes `trade.submitted.v1` partitioned by `instrumentId`
2. Risk Engine and AML Engine independently consume it and run their checks
3. Trade Processor receives `risk.approved` and `aml.cleared` independently
4. Spring State Machine transitions the trade through states as signals arrive
5. Only when state reaches `AML_CLEARED` does the state machine transition to `EXECUTING` and emit `trade.executed.v1`
6. If either service emits a rejection, the state machine transitions to `REJECTED` and emits `trade.rejected.v1` with a compensating action
7. PostgreSQL tracks partial approval state — if both approvals don't arrive within 30 seconds, a scheduled cleanup job auto-rejects the trade

## Why Choreography for Cross-Service
- No single point of failure — Risk Engine and AML Engine operate independently
- Loose coupling — Trade Processor does not know the internal logic of either service
- Independent scalability — each service scales on its own
- Kafka provides durability — if a service goes down, it replays events on recovery

## Why Local Orchestration for Intra-Service
- The approval workflow is sequential and order-dependent
- State must be tracked precisely across two independent signals
- Immediate visibility into trade lifecycle for audit and debugging
- Clean rollback — state machine knows exactly where to compensate on failure

## Why Spring State Machine over Temporal
Temporal is the production-grade choice for durable workflow orchestration and is what banks like JPMorgan use in practice. Spring State Machine is used here because:
- Zero additional infrastructure — runs inside the Trade Processor JVM
- Sufficient for this workflow's complexity and scope
- Buildable within project timeline

The Trade Processor's workflow interfaces are designed so Temporal can be dropped in as a replacement without changing business logic.

## Alternatives Rejected
- **Pure choreography end-to-end** — managing partial approval state across two independent signals without a local state machine produces complex, hard-to-debug event spaghetti
- **Central Saga Orchestrator** — single point of failure, tight coupling across services
- **Temporal** — correct production choice but requires dedicated infrastructure and has significant operational overhead for this scope

## Consequences
- Spring State Machine adds a dependency to the Trade Processor Service
- State machine transitions must be idempotent — duplicate events must not cause double transitions
- Cleanup job must run frequently enough to prevent stale trades accumulating