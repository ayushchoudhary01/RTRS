# 007 — Race Condition: AML/Risk Event Arrives Before Approval State Initiated

## Symptom
`AmlClearedConsumer` throws an exception and trade times out despite AML and Risk
both approving successfully:

```
AML cleared event received. tradeId=cc1d2785...
Failed to process aml.cleared event. error=No approval state found for tradeId: cc1d2785

Approval state initiated. tradeId=cc1d2785...   ← arrived AFTER the error
```

## Root Cause
Three services consume `trade.submitted.v1` independently:
- `trade-processor-service` → creates approval state in PostgreSQL
- `risk-engine-service` → evaluates risk → publishes `risk.approved.v1`
- `aml-engine-service` → evaluates AML → publishes `aml.cleared.v1`

Risk-engine and AML-engine are stateless and fast — they evaluate and publish
in milliseconds. `trade-processor-service` must write to PostgreSQL before the
approval state exists. Under normal load, the evaluation engines finish and
publish their outcome events before `trade-processor` has committed the approval
state row.

When `AmlClearedConsumer` or `RiskApprovedConsumer` receives the outcome event
and calls `markAmlCleared()` / `markRiskCleared()`, the `trade_approval_state`
row doesn't exist yet — causing `findOrThrow()` to throw `IllegalStateException`.

## Fix
**1. Graceful handling in `TradeApprovalAggregator`**

Replace `findOrThrow()` in `markRiskCleared()` and `markAmlCleared()` with an
`Optional` check that returns `false` instead of throwing:

```java
Optional<TradeApprovalState> stateOpt = approvalStateRepository.findByTradeIdForUpdate(tradeId);
if (stateOpt.isEmpty()) {
    log.warn("Approval state not yet created for tradeId={}. Event arrived early.", tradeId);
    return false;
}
```

**2. Kafka retry in consumers**

Re-throw as `RuntimeException` in the `IllegalStateException` catch block so
Kafka's retry mechanism re-delivers the message after a short backoff:

```java
} catch (IllegalStateException ex) {
    log.warn("Approval state not found for tradeId={}, will retry.", tradeId);
    throw new RuntimeException(ex);
}
```

Move `tradeId` extraction outside the try block so it's accessible in the
catch block.

## Result
On retry, the approval state exists and `markAmlCleared()` / `markRiskCleared()`
succeeds. The happy path completes correctly:

```
Approval state initiated. tradeId=1863536c...
AML cleared for tradeId=1863536c... bothCleared=false
Risk cleared for tradeId=1863536c... bothCleared=true
Trade executed successfully. tradeId=1863536c...
```

## Affected
- `RiskApprovedConsumer` in `trade-processor-service`
- `AmlClearedConsumer` in `trade-processor-service`
- `TradeApprovalAggregator.markRiskCleared()` and `markAmlCleared()`