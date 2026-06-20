CREATE TABLE reconciliation_trade_facts
(
    trade_id          UUID PRIMARY KEY,
    account_id        UUID            NOT NULL,
    instrument_id     VARCHAR(50)     NOT NULL,
    quantity          NUMERIC(38, 10) NOT NULL,
    limit_price       NUMERIC(38, 10) NOT NULL,
    currency          VARCHAR(10)     NOT NULL,
    executed_at       TIMESTAMPTZ     NOT NULL,
    status            VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    received_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_reconciled_at TIMESTAMPTZ,

    CONSTRAINT chk_trade_fact_status CHECK (status IN ('PENDING', 'RECONCILED', 'BROKEN'))
);

CREATE INDEX idx_trade_facts_status ON reconciliation_trade_facts (status);
CREATE INDEX idx_trade_facts_executed_at ON reconciliation_trade_facts (executed_at);

CREATE TABLE reconciliation_ledger_facts
(
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trade_id          UUID            NOT NULL,
    journal_id        UUID            NOT NULL,
    entry_type        VARCHAR(10)     NOT NULL,
    amount            NUMERIC(38, 10) NOT NULL,
    currency          VARCHAR(10)     NOT NULL,
    ledger_created_at TIMESTAMPTZ     NOT NULL,
    received_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_ledger_fact_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT uq_ledger_fact_journal_entry_type UNIQUE (journal_id, entry_type)
);

CREATE INDEX idx_ledger_facts_trade_id ON reconciliation_ledger_facts (trade_id);

CREATE TABLE reconciliation_breaks
(
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trade_id       UUID        NOT NULL,
    break_type     VARCHAR(30) NOT NULL,
    severity       VARCHAR(10) NOT NULL,
    expected_value TEXT,
    actual_value   TEXT,
    status         VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    detected_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at    TIMESTAMPTZ,

    CONSTRAINT chk_break_type CHECK (break_type IN (
                                                    'MISSING_IN_LEDGER', 'MISSING_IN_PROCESSOR', 'MISSING_DEBIT', 'MISSING_CREDIT',
                                                    'UNBALANCED_DOUBLE_ENTRY', 'DUPLICATE_LEDGER_ENTRY', 'AMOUNT_MISMATCH', 'CURRENCY_MISMATCH'
        )),
    CONSTRAINT chk_break_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM')),
    CONSTRAINT chk_break_status CHECK (status IN ('OPEN', 'RESOLVED'))
);

CREATE INDEX idx_breaks_status ON reconciliation_breaks (status);
CREATE INDEX idx_breaks_trade_id ON reconciliation_breaks (trade_id);

CREATE TABLE reconciliation_runs
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    started_at      TIMESTAMPTZ NOT NULL,
    completed_at    TIMESTAMPTZ,
    trades_checked  INT         NOT NULL DEFAULT 0,
    breaks_found    INT         NOT NULL DEFAULT 0,
    breaks_resolved INT         NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL,
    duration_ms     BIGINT,
    failure_reason  TEXT,

    CONSTRAINT chk_run_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_runs_started_at ON reconciliation_runs (started_at);