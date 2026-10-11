# 016 — Every BUY Escalates With "Insufficient Cash Balance For Payment"

## Symptom
Settlement runs, nets correctly, and then fails the DVP check on every single BUY
obligation, regardless of the account or the amount:

```json
{"status":"ESCALATED","retryCount":3,
 "failureReason":"Insufficient cash balance for payment",
 "tradeType":"BUY","quantity":10.0000000000,"unitPrice":100.0000000000}
```

The account's ledger shows entries and an intact hash chain, so the ledger is
clearly working. Its balance is `0.00`.

## Root Cause
`DvpValidator` asks ledger-service for the account's cash balance and requires
`balance >= required cash`. The balance is always zero.

`LedgerService.recordTrade()` writes a DEBIT and a CREDIT **of the same amount to
the same account** for every trade:

```java
BigDecimal balanceAfterDebit  = balance.getBalance().subtract(totalAmount);
BigDecimal balanceAfterCredit = balance.getBalance().add(totalAmount);
```

Each trade therefore nets to exactly zero and the running balance never leaves
`0.00`, no matter how many trades are posted.

The double-entry mechanics are correct — paired entries, always balanced, written
atomically under a pessimistic lock. What is missing is the *economics*: the ledger
models a single account per trade rather than a cash account and a securities
account, so there is no account for value to move **between**, and no operation
through which cash can enter an account in the first place. A deposit or funding
flow does not exist.

So the DVP cash leg is checking a real balance, and that balance is structurally
always zero.

## Fix
**For a demo:** seed the projection directly.

```
docker exec -i rtrs-postgres-ledger psql -U rtrs_ledger -d ledger_db \
  -v account=<uuid> -v amount=1000000 < demo/seed-account-cash.sql
```

This is safe because `account_balances` is a projection that `LedgerService`
already rewrites in place on every posting. Only `ledger_entries` is append-only
and trigger-protected, and the script does not touch it — the hash chain still
verifies afterwards, because the chain is computed over entry fields and never
over this balance. The upsert writes only `balance`, deliberately leaving
`entry_count` and `last_entry_hash` alone, since those are the account's chain
bookkeeping and resetting them would detach the chain from its entries.

**The real fix**, not implemented, is a two-account model (cash + securities) with
an explicit funding operation, so that a BUY moves value between accounts instead
of debiting and crediting the same one.

## Result
With the account funded, the same trade shape settles on the first attempt:

```json
{"status":"SETTLED","retryCount":0,"failureReason":null}
```

## Gotcha: `ESCALATED` Is Terminal
Funding the account does **not** rescue an instruction that has already escalated.
`SETTLEABLE_STATUSES` is `PENDING` and `RETRYING` only, so an `ESCALATED` row is
never selected again — by design, since escalation means manual intervention.
Observed directly: an instruction escalated at 05:49, the account was funded at
05:51, and the instruction stayed `ESCALATED`. A trade submitted after funding
settled immediately. Fund the account **before** submitting the trade.

## Affected
- `DvpValidator` in `settlement-service`
- `LedgerService.recordTrade()` in `ledger-service`
- `demo/seed-account-cash.sql`
