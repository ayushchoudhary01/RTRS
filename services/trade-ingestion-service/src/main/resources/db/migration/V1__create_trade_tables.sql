-- Trade details immutable after creation; lifecycle managed via status transitions
CREATE TABLE trades (
                        id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        client_order_ref    VARCHAR(100) NOT NULL,
                        account_id          UUID NOT NULL,
                        instrument_id       VARCHAR(50) NOT NULL,
                        trade_type          VARCHAR(10) NOT NULL,
                        quantity            NUMERIC(38, 10) NOT NULL,
                        limit_price         NUMERIC(38, 10) NOT NULL,
                        currency            VARCHAR(3) NOT NULL,
                        status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                        market              VARCHAR(20),
                        created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                        CONSTRAINT chk_trade_type CHECK (trade_type IN ('BUY', 'SELL', 'SHORT', 'COVER')),
                        CONSTRAINT chk_trade_status CHECK (status IN ('PENDING', 'VALIDATED', 'RISK_APPROVED', 'RISK_REJECTED', 'AML_CLEARED', 'AML_FLAGGED', 'EXECUTED', 'REJECTED', 'SETTLING', 'SETTLED', 'FAILED')),
                        CONSTRAINT chk_quantity_positive CHECK (quantity > 0),
                        CONSTRAINT chk_price_positive CHECK (limit_price > 0),
                        CONSTRAINT uk_client_order_ref UNIQUE (client_order_ref)
);

-- Trigger to keep updated_at current on every row update
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_trades_updated_at
    BEFORE UPDATE ON trades
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- Idempotency table — prevents duplicate trade submissions
CREATE TABLE idempotency_keys (
                                  idempotency_key     VARCHAR(255) PRIMARY KEY,
                                  trade_id            UUID NOT NULL REFERENCES trades(id),
                                  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                  expires_at          TIMESTAMPTZ NOT NULL,

                                  CONSTRAINT uk_idempotency_trade_id UNIQUE (trade_id)
);

-- Outbox table — transactional outbox pattern
CREATE TABLE outbox_events (
                               id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               aggregate_type      VARCHAR(100) NOT NULL,
                               aggregate_id        VARCHAR(255) NOT NULL,
                               event_type          VARCHAR(255) NOT NULL,
                               topic               VARCHAR(255) NOT NULL,
                               partition_key       VARCHAR(255) NOT NULL,
                               payload             JSONB NOT NULL,
                               processed           BOOLEAN NOT NULL DEFAULT FALSE,
                               attempt_count       INTEGER NOT NULL DEFAULT 0,
                               last_error          TEXT,
                               created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                               processed_at        TIMESTAMPTZ
);

-- Indexes
CREATE INDEX idx_trades_account_id ON trades (account_id);
CREATE INDEX idx_trades_instrument_id ON trades (instrument_id);
CREATE INDEX idx_trades_status ON trades (status);
CREATE INDEX idx_trades_created_at ON trades (created_at DESC);
CREATE INDEX idx_idempotency_expires_at ON idempotency_keys (expires_at);
CREATE INDEX idx_outbox_unprocessed ON outbox_events (created_at ASC) WHERE processed = FALSE;