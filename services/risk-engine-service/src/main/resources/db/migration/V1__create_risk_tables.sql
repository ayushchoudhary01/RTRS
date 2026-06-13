CREATE TABLE risk_evaluations
(
    id               UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    trade_id         UUID        NOT NULL UNIQUE,
    instrument_id    VARCHAR(50) NOT NULL,
    account_id       UUID        NOT NULL,
    quantity         NUMERIC(38, 10) NOT NULL,
    limit_price      NUMERIC(38, 10) NOT NULL,
    notional_usd     NUMERIC(38, 10) NOT NULL,
    outcome          VARCHAR(20) NOT NULL,
    breach_reason    TEXT,
    breached_rule    VARCHAR(100),
    evaluated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_risk_outcome CHECK (outcome IN ('APPROVED', 'BREACHED'))
);

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_risk_evaluations_updated_at
    BEFORE UPDATE ON risk_evaluations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE INDEX idx_risk_evaluations_trade_id ON risk_evaluations (trade_id);
CREATE INDEX idx_risk_evaluations_instrument_id ON risk_evaluations (instrument_id);
CREATE INDEX idx_risk_evaluations_outcome ON risk_evaluations (outcome);
CREATE INDEX idx_risk_evaluations_evaluated_at ON risk_evaluations (evaluated_at DESC);