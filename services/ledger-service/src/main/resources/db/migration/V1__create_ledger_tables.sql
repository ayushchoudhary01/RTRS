CREATE TABLE ledger_journals
(
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trade_id     UUID        NOT NULL UNIQUE,
    account_id   UUID        NOT NULL,
    instrument_id VARCHAR(50) NOT NULL,
    currency     VARCHAR(10) NOT NULL,
    quantity     NUMERIC(38, 10) NOT NULL,
    unit_price   NUMERIC(38, 10) NOT NULL,
    total_amount NUMERIC(38, 10) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_journal_status CHECK (status IN ('POSTED', 'VOIDED'))
);

CREATE TABLE ledger_entries
(
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_id   UUID        NOT NULL REFERENCES ledger_journals (id),
    account_id   UUID        NOT NULL,
    entry_type   VARCHAR(10) NOT NULL,
    amount       NUMERIC(38, 10) NOT NULL,
    currency     VARCHAR(10) NOT NULL,
    balance_after NUMERIC(38, 10) NOT NULL,
    entry_hash   VARCHAR(64) NOT NULL,
    prev_hash    VARCHAR(64) NOT NULL,
    sequence_num BIGINT      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_amount_positive CHECK (amount > 0)
);

CREATE TABLE account_balances
(
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id   UUID           NOT NULL UNIQUE,
    balance      NUMERIC(38, 10) NOT NULL DEFAULT 0,
    currency     VARCHAR(10)    NOT NULL DEFAULT 'USD',
    entry_count  BIGINT         NOT NULL DEFAULT 0,
    last_entry_hash VARCHAR(64),
    last_entry_at TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Prevent any UPDATE or DELETE on ledger_entries — immutable append-only
CREATE OR REPLACE FUNCTION prevent_ledger_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only. UPDATE and DELETE are not permitted.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entries_immutable
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_mutation();

CREATE OR REPLACE FUNCTION update_account_balance_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_account_balances_updated_at
    BEFORE UPDATE ON account_balances
    FOR EACH ROW EXECUTE FUNCTION update_account_balance_updated_at();

CREATE INDEX idx_ledger_entries_journal_id ON ledger_entries (journal_id);
CREATE INDEX idx_ledger_entries_account_id ON ledger_entries (account_id);
CREATE INDEX idx_ledger_entries_created_at ON ledger_entries (created_at DESC);
CREATE INDEX idx_ledger_entries_sequence ON ledger_entries (account_id, sequence_num);
CREATE INDEX idx_ledger_journals_trade_id ON ledger_journals (trade_id);
CREATE INDEX idx_ledger_journals_account_id ON ledger_journals (account_id);
CREATE INDEX idx_account_balances_account_id ON account_balances (account_id);