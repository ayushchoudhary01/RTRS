# 09 — Rejection Paths: What Happens When Risk or AML Says No

Two real rejected trades, traced with actual log output. Both prove the same
underlying mechanism: trade-processor never has a dedicated "handle a
rejection" code path — there's no `RiskBreachedConsumer` or
`AmlFlaggedConsumer`. Instead, a rejection is simply the *absence* of a
clearance, discovered 30 seconds later by `ChoreographyTimeoutHandler`.

## Case 1 — AML Rejects, Risk Approves

**Trade:** `ORDER-COVERAGE-002`, BUY 440 units of AAPL at $150.00.
Notional: $66,000.00.

```
19:05:52  TradeApprovalAggregator — Approval state initiated.
          tradeId=86513e8d-bc3f-425d-a6a3-4bbbdfa08996

risk-engine:
19:05:52  RiskEvaluationService — Trade risk approved.
          tradeId=86513e8d..., notionalUsd=66000.00

aml-engine:
19:05:52  AmlEvaluationService — Trade AML flagged.
          tradeId=86513e8d..., rule=ROUND_AMOUNT, riskScore=80
```

$66,000 is well under risk's $1,000,000 position limit, so risk approved it
cleanly. But $66,000 is an exact multiple of $1,000 and above
`RoundAmountRule`'s $50,000 threshold — AML flagged it. `riskScore=80`
reflects `RoundAmountRule` firing as the third rule in the `@Order` sequence
(`100 - (2 × 10) = 80`).

Back in trade-processor:

```
19:05:52  RiskApprovedConsumer — Risk approved event received.
19:05:52  TradeApprovalAggregator — Risk cleared. bothCleared=false
```

That's the **last** thing that happens for this trade until the timeout.
`aml.flagged.v1` was published, but trade-processor has no consumer
listening for it — `AmlClearedConsumer` only exists for `aml.cleared.v1`.
The `riskCleared` flag is `true`; the `amlCleared` flag stays `false`
forever, because nothing will ever set it.

30 seconds later:

```
19:06:32  ChoreographyTimeoutHandler — Stale approvals found. count=1
19:06:32  CompensatingActionService — Compensating event saved.
          tradeId=86513e8d..., reason=TIMEOUT
19:06:32  ChoreographyTimeoutHandler — Trade timed out and rejected.
```

`trade.rejected.v1` is published via the outbox. The trade never reaches
`trade.executed.v1` — ledger, settlement, and reconciliation never see it at
all, which is exactly correct: a rejected trade should leave no financial
footprint anywhere downstream.

## Case 2 — Both Risk and AML Independently Reject the Same Trade

**Trade:** `ORDER-COVERAGE-003`, BUY 10,000 units of AAPL at $150.00.
Notional: $1,500,000.00.

```
19:06:05  TradeApprovalAggregator — Approval state initiated.
          tradeId=dc0906f2-857e-426a-b37f-7976899a7942

risk-engine:
19:06:05  RiskEvaluationService — Trade risk breached.
          tradeId=dc0906f2..., rule=POSITION_LIMIT,
          reason=Notional USD 1500000.00 exceeds position limit 1000000.00

aml-engine:
19:06:05  AmlEvaluationService — Trade AML flagged.
          tradeId=dc0906f2..., rule=HIGH_VALUE_TRANSACTION, riskScore=100
```

This is the more interesting case: $1.5M exceeds risk's $1M position limit
*and* exceeds AML's $500K high-value threshold. Two completely independent
rule engines, evaluating the same trade with no knowledge of each other,
both correctly conclude it's a problem — for different, equally valid
reasons. `riskScore=100` reflects `HighValueTransactionRule` firing first in
AML's `@Order` sequence (`100 - (0 × 10) = 100`), the maximum severity
score.

Neither `risk.breached.v1` nor `aml.flagged.v1` has a listener in
trade-processor. Both flags — `riskCleared` and `amlCleared` — stay `false`.
30 seconds later:

```
19:06:42  ChoreographyTimeoutHandler — Stale approvals found. count=1
19:06:42  CompensatingActionService — Compensating event saved.
          tradeId=dc0906f2..., reason=TIMEOUT
19:06:42  ChoreographyTimeoutHandler — Trade timed out and rejected.
```

Same outcome, same mechanism, even though the *reason* for rejection is
doubly redundant here. The system doesn't need to know that both rules
fired to reject correctly — the timeout doesn't care *why* a trade never
cleared, only *that* it didn't.

## What This Design Trades Off

The honest cost of this approach: every rejection takes the full 30 seconds,
even when the actual rejection reason (a breach or a flag) was known
instantly. A more responsive design would have trade-processor listen for
`risk.breached.v1` and `aml.flagged.v1` directly and reject immediately. The
trade-off made instead was simplicity — one rejection code path
(`ChoreographyTimeoutHandler`) instead of three (immediate-on-breach,
immediate-on-flag, and the timeout as a fallback for genuinely lost
messages). The timeout path already has to exist regardless, to handle the
case where a message is silently dropped — so the question was whether to
*also* build fast-path rejection on top of it, and the answer here was no,
not yet. This is worth being able to articulate plainly in an interview: it
is a real, known limitation, not something the system fails at silently.