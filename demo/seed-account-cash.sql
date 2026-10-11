-- Demo fixture: funds an account so a BUY can pass the DVP cash check
--
-- Defaults to the original demo account and 1,000,000 USD:
--   docker exec -i rtrs-postgres-ledger psql -U rtrs_ledger -d ledger_db < demo/seed-account-cash.sql
--
-- Any account, any amount:
--   docker exec -i rtrs-postgres-ledger psql -U rtrs_ledger -d ledger_db \
--     -v account=9e1c7a40-3b55-4d2e-8f61-0a7b5c2d4e10 -v amount=250000 \
--     < demo/seed-account-cash.sql
--
-- WHY THIS IS NEEDED
--
-- LedgerService writes a DEBIT and a CREDIT of the same amount to the SAME
-- account for every trade, so each trade nets to zero and the balance never
-- leaves 0.00. settlement-service's DvpValidator asks ledger-service for the
-- cash balance and requires balance >= required cash, so every BUY obligation
-- fails with "Insufficient cash balance for payment" and ends up ESCALATED
-- after exhausting its retries.
--
-- That is a consequence of the ledger modelling one account rather than a cash
-- account and a securities account separately. The double-entry mechanics are
-- correct — paired entries, always balanced, written atomically — but the
-- economics are not modelled, so there is no path for cash to enter an account.
-- A real fix is a two-account model plus a funding/deposit operation, not this
-- script. See docs/architecture/05-ledger-service.md
--
-- WHY IT IS SAFE
--
-- account_balances is a projection that LedgerService already updates in place
-- on every posting. Only ledger_entries is append-only, protected by
-- trg_ledger_entries_immutable, and this script does not touch it. The hash
-- chain is unaffected, because it is computed over entry fields and never over
-- this balance.
--
-- The upsert deliberately writes ONLY the balance. entry_count and
-- last_entry_hash are the account's chain bookkeeping — resetting them on an
-- account that has already traded would detach the chain from its entries and
-- corrupt the next posting. updated_at is left to trg_account_balances_updated_at

\if :{?account}
\else
\set account 550e8460-e29b-41d4-a716-446655440000
\endif

\if :{?amount}
\else
\set amount 1000000
\endif

INSERT INTO account_balances (account_id, balance, currency, entry_count)
VALUES (:'account'::uuid, :amount, 'USD', 0)
ON CONFLICT (account_id) DO UPDATE
SET balance = EXCLUDED.balance;

SELECT account_id, balance, currency, entry_count, last_entry_hash
FROM account_balances
WHERE account_id = :'account'::uuid;
