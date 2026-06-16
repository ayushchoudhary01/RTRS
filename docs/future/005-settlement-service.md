# 005 — Settlement Service (T+2)

## Current State
Trade lifecycle ends at `trade.executed.v1`. There is no settlement step —
trades are marked FILLED but actual delivery of securities and cash transfer
is not modelled.

## Planned Improvement

### Settlement Cycle
```
trade.executed.v1
↓
settlement-service creates SettlementInstruction
↓
Status: PENDING_SETTLEMENT
↓
T+2 (2 business days later)
↓
DVP check: securities available + cash available
↓
If both available: SETTLED → ledger updated
If failed: FAILED → compensation + retry or manual intervention
```

### DVP (Delivery vs Payment)
The core settlement principle — securities are delivered only if and only if
payment is received simultaneously. Prevents counterparty risk.

### Key Components
- `SettlementInstruction` — created per executed trade
- `SettlementScheduler` — runs at T+2, attempts settlement
- `DVPValidator` — checks securities inventory + cash balance
- `SettlementFailureHandler` — retry logic + manual intervention queue
- `settlement.settled.v1` / `settlement.failed.v1` Kafka events

### Database
- `settlement_instructions` table — append only, status transitions tracked
- `settlement_db` on port 5437 (already provisioned in docker-compose)

## Regulatory Context
- T+2 is the standard settlement cycle for equities (SEBI, SEC, FCA)
- T+1 is being phased in by SEC from 2024
- Same-day settlement (T+0) exists for some FX trades



