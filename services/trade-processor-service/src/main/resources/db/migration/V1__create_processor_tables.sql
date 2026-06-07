CREATE TABLE trade_approval_state
(
    id               UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    trade_id         UUID        NOT NULL UNIQUE,
    instrument_id    VARCHAR(50) NOT NULL,
    account_id       UUID        NOT NULL,
    risk_cleared     BOOLEAN     NOT NULL DEFAULT FALSE,
    aml_cleared      BOOLEAN     NOT NULL DEFAULT FALSE,
    risk_cleared_at  TIMESTAMPTZ,
    aml_cleared_at   TIMESTAMPTZ,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_approval_status CHECK (status IN
                                          ('PENDING', 'RISK_CLEARED', 'AML_CLEARED', 'APPROVED', 'REJECTED', 'TIMEOUT'))
);

CREATE
OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at
= NOW();
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER trg_approval_state_updated_at
    BEFORE UPDATE
    ON trade_approval_state
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TABLE outbox_events
(
    id             UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    event_type     VARCHAR(255) NOT NULL,
    topic          VARCHAR(255) NOT NULL,
    partition_key  VARCHAR(255) NOT NULL,
    payload        JSONB        NOT NULL,
    processed      BOOLEAN      NOT NULL DEFAULT FALSE,
    attempt_count  INTEGER      NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at   TIMESTAMPTZ
);

CREATE INDEX idx_approval_state_trade_id ON trade_approval_state (trade_id);
CREATE INDEX idx_approval_state_status ON trade_approval_state (status);
CREATE INDEX idx_approval_state_created_at ON trade_approval_state (created_at DESC);
CREATE INDEX idx_outbox_unprocessed ON outbox_events (created_at ASC) WHERE processed = FALSE;