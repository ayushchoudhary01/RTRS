# 08 — The Happy Path: One Trade, Traced Through All Seven Services

This is a real trade, traced end to end using actual log output from a test
run. Every timestamp, ID, and log line below is genuine — nothing here is a
hypothetical example.

**Trade:** `ORDER-COVERAGE-001`, BUY 137 units of AAPL at $173.42, account
`550e8460-e29b-41d4-a716-446655440000`, USD. Notional: $23,758.54.

## 1. Submission

```
POST /api/v1/trades  →  trade-ingestion-service
```

`TradeController` receives the request, `TradeValidationService` confirms
it's well-formed, `IdempotencyService` confirms `ORDER-COVERAGE-001` hasn't
been seen before, and `TradeIngestionService` writes the `Trade` row and an
`OutboxEvent` in one transaction. Within ~100ms, `OutboxPublisher` relays it:

```
19:01:48  TradeSubmittedConsumer — Trade submitted event received.
          offset=17, key=AAPL, partition=0
19:01:48  TradeApprovalAggregator — Approval state initiated.
          tradeId=03a8273a-5455-4362-993b-7ad3bdfc9e40
```

That second line is **trade-processor**, not trade-ingestion — `key=AAPL`
confirms partitioning by instrument worked as designed, and the approval
state row now exists for risk-engine and aml-engine's eventual verdicts to
attach to.

## 2. Parallel Evaluation

Risk-engine and AML-engine both consume the same `trade.submitted.v1`
message independently. Neither knows the other exists.

```
risk-engine:
19:01:50  RiskEvaluationService — Trade risk approved.
          tradeId=03a8273a..., notionalUsd=23758.540
19:01:50  RiskEventProducer — RiskApproved published.

aml-engine:
19:01:50  AmlEvaluationService — Trade AML cleared.
          tradeId=03a8273a..., riskScore=25
19:01:50  AmlEventProducer — AmlCleared published.
```

`riskScore=25` here is the AML rule chain's "all rules passed" score — five
rules × 5 points each, confirming every one of `HighValueTransactionRule`
through `SanctionedCountryRule` ran and cleared this trade. $23,758.54 is
well under the $500K high-value threshold, not in the $8K–$10K structuring
band, not a round number, and AAPL/USD isn't sanctioned — a clean pass on
every rule.

Both events are published via **Avro**, not JSON — this is the one moment
in the pipeline where the wire format switches from the outbox's JSON to
direct Avro publish (see `docs/system-design/00-overview.md` for why).

## 3. Aggregation and Execution

Both verdicts arrive at trade-processor, each on its own consumer thread:

```
19:01:50  RiskApprovedConsumer — Risk approved event received.
19:01:50  AmlClearedConsumer — AML cleared event received.
19:01:50  TradeApprovalAggregator — Risk cleared. bothCleared=false
19:01:50  TradeApprovalAggregator — AML cleared. bothCleared=true
19:01:50  TradeExecutionService — Executing trade.
19:01:50  TradeExecutionService — Trade executed successfully.
```

Notice `bothCleared=false` immediately followed by `bothCleared=true` — risk's
verdict arrived and flipped one flag, then AML's verdict arrived a moment
later and flipped the second, at which point `markAmlCleared()` returned
`true` and `TradeExecutionService.execute()` ran. The pessimistic lock on
`TradeApprovalState` is what makes this sequence safe rather than a race —
each consumer thread had to wait its turn to read-and-update the row.

`trade.executed.v1` is now published via the outbox, carrying the full
enriched payload (`tradeId`, `accountId`, `instrumentId`, `quantity`,
`limitPrice`, `currency`) that ledger, settlement, and reconciliation all
need.

## 4. Ledger Posting

```
19:01:51  TradeExecutedConsumer — Trade executed event received for ledger.
19:01:51  LedgerService — Double-entry ledger posted.
          tradeId=03a8273a..., journalId=671963a3-7a17-4f4c-83be-9451d45f578c,
          totalAmount=23758.5400000000
```

One `LedgerJournal` row, two `LedgerEntry` rows (DEBIT and CREDIT), both
hash-chained from the account's previous entry, `AccountBalance` updated
under a pessimistic lock. Both entries are also written to the outbox,
targeting `ledger.entry.created.v1`.

## 5. Settlement Instruction

```
19:01:50  TradeExecutedConsumer — Trade executed event received for settlement.
19:01:50  TradeExecutedConsumer — Settlement instruction created.
          tradeId=03a8273a..., settlementDate=2026-06-23
```

A `SettlementInstruction` is created in `PENDING` status, settlement date
set two calendar days out, confirming the T+2 calculation
(`settlement-cycle-days: 2`) ran correctly. `PositionBalance` for this
account/instrument pair is also updated here, immediately on execution — not
deferred to settlement day.

## 6. Reconciliation

```
19:01:50  TradeExecutedConsumer (reconciliation) — Trade fact recorded.
19:01:51  LedgerEntryCreatedConsumer — Ledger fact recorded. entryType=DEBIT
19:01:51  LedgerEntryCreatedConsumer — Ledger fact recorded. entryType=CREDIT
...
19:02:40  ReconciliationScheduler — Reconciliation run completed.
          tradesChecked=1, breaksFound=0, breaksResolved=0
```

One `TradeFact` and two `LedgerFact`s (DEBIT, CREDIT) are recorded as the
two source events arrive independently. The very next scheduled run — about
a minute later, after the 30-second grace window had already passed — picks
this trade up, runs every break check in `ReconciliationEngine.evaluate()`,
and finds nothing wrong: quantities match, amounts match, currencies match,
both legs of the double-entry are present and balanced.

## What This Proves

Seven services, one trade, zero unexplained failures. The choreography saga
correctly aggregated two independent verdicts under concurrent access, the
state machine and outbox pattern correctly carried the trade through
execution without losing data, double-entry bookkeeping balanced correctly,
a T+2 settlement instruction was created with the right date math, and an
independent reconciliation pass — using none of the same code paths as the
trade pipeline itself — verified everything matched. This is what "the
system works end to end" actually means here: not that no service threw an
exception, but that an independently-built verification layer checked the
work and found it correct.