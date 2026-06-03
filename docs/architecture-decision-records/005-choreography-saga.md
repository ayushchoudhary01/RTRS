# 005 — Choreography-Based Saga over Orchestration

## Status
Accepted

## Decision
The trade approval flow uses choreography — services react to events independently. There is no central saga orchestrator.

## Context
A trade must be approved by both the Risk Engine and the AML Engine before it can be executed. We needed a way to coordinate this multi-service approval without creating a central coordinator that becomes a single point of failure.

## Reasons
- **No single point of failure** — a central orchestrator going down would halt all trade processing. With choreography, each service operates independently.
- **Loose coupling** — the Trade Processor does not need to know the internal logic of the Risk Engine or AML Engine. It only reacts to their output events.
- **Independent scalability** — Risk Engine and AML Engine can be scaled independently.
- **Simpler failure model** — if the Risk Engine rejects a trade, it emits `risk.breached`. The Trade Processor reacts and emits `trade.rejected`. No orchestrator needs to track state.

## How It Works
1. Trade Ingestion publishes `trade.submitted`
2. Risk Engine and AML Engine independently consume it and run their checks
3. Trade Processor waits for both `risk.approved` AND `aml.cleared` via `TradeApprovalAggregator` (state stored in PostgreSQL)
4. If either rejects → `trade.rejected` + compensating action
5. Redis TTL handles timeout — if approval does not arrive within 30 seconds, the trade is auto-rejected

## Alternatives Rejected
- **Central Saga Orchestrator** — single point of failure, tight coupling, harder to scale.
- **Temporal/Camunda** — powerful but massive operational overhead for this scope.

## Consequences
- `TradeApprovalAggregator` must handle partial state — one approval arrived, the other has not yet.
- Timeout handling is critical — Redis TTL ensures no trade is left in limbo indefinitely.
