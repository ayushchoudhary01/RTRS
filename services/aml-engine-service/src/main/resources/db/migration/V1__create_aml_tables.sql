CREATE TABLE aml_evaluations
(
    id               UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    trade_id         UUID        NOT NULL UNIQUE,
    instrument_id    VARCHAR(50) NOT NULL,
    account_id       UUID        NOT NULL,
    notional_usd     NUMERIC(38, 10) NOT NULL,
    outcome          VARCHAR(20) NOT NULL,
    flagged_reason   TEXT,
    flagged_rule     VARCHAR(100),
    risk_score       INTEGER     NOT NULL DEFAULT 0,
    evaluated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_aml_outcome CHECK (outcome IN ('CLEARED', 'FLAGGED'))
);

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_aml_evaluations_updated_at
    BEFORE UPDATE ON aml_evaluations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE INDEX idx_aml_evaluations_trade_id ON aml_evaluations (trade_id);
CREATE INDEX idx_aml_evaluations_account_id ON aml_evaluations (account_id);
CREATE INDEX idx_aml_evaluations_outcome ON aml_evaluations (outcome);
CREATE INDEX idx_aml_evaluations_evaluated_at ON aml_evaluations (evaluated_at DESC);