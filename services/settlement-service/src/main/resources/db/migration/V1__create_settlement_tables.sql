CREATE TABLE settlement_runs
(
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    started_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at         TIMESTAMPTZ,
    status               VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    instructions_processed INTEGER   NOT NULL DEFAULT 0,
    success_count        INTEGER    NOT NULL DEFAULT 0,
    failed_count          INTEGER   NOT NULL DEFAULT 0,
    failure_reason        TEXT,

    CONSTRAINT chk_settlement_run_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE settlement_instructions
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trade_id            UUID        NOT NULL UNIQUE,
    account_id          UUID        NOT NULL,
    instrument_id       VARCHAR(50) NOT NULL,
    trade_type          VARCHAR(10) NOT NULL,
    quantity            NUMERIC(38, 10) NOT NULL,
    unit_price          NUMERIC(38, 10) NOT NULL,
    currency            VARCHAR(10) NOT NULL,
    settlement_date     DATE        NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    netted_obligation_id UUID,
    settlement_run_id   UUID REFERENCES settlement_runs (id),
    retry_count         INTEGER     NOT NULL DEFAULT 0,
    failure_reason      TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_settlement_status CHECK (
        status IN ('PENDING', 'CONFIRMED', 'CLEARING', 'SETTLED', 'FAILED', 'RETRYING', 'ESCALATED')
        ),
    CONSTRAINT chk_settlement_trade_type CHECK (trade_type IN ('BUY', 'SELL'))
);

CREATE TABLE netted_obligations
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID        NOT NULL,
    instrument_id   VARCHAR(50) NOT NULL,
    settlement_date DATE        NOT NULL,
    net_quantity    NUMERIC(38, 10) NOT NULL,
    net_direction   VARCHAR(10) NOT NULL,
    instruction_count INTEGER   NOT NULL,
    settlement_run_id UUID REFERENCES settlement_runs (id),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_netted_direction CHECK (net_direction IN ('BUY', 'SELL')),
    CONSTRAINT chk_netted_status CHECK (status IN ('PENDING', 'SETTLED', 'FAILED')),
    CONSTRAINT uq_netted_obligation UNIQUE (account_id, instrument_id, settlement_date, settlement_run_id)
);

-- Minimal position tracking — required for genuine DVP (cash + security check)
CREATE TABLE position_balances
(
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id    UUID        NOT NULL,
    instrument_id VARCHAR(50) NOT NULL,
    quantity      NUMERIC(38, 10) NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_position_account_instrument UNIQUE (account_id, instrument_id)
);

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

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_settlement_instructions_updated_at
    BEFORE UPDATE ON settlement_instructions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_position_balances_updated_at
    BEFORE UPDATE ON position_balances
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE INDEX idx_settlement_instructions_trade_id ON settlement_instructions (trade_id);
CREATE INDEX idx_settlement_instructions_status ON settlement_instructions (status);
CREATE INDEX idx_settlement_instructions_settlement_date ON settlement_instructions (settlement_date);
CREATE INDEX idx_settlement_instructions_account_instrument ON settlement_instructions (account_id, instrument_id);
CREATE INDEX idx_netted_obligations_account_instrument ON netted_obligations (account_id, instrument_id, settlement_date);
CREATE INDEX idx_position_balances_account ON position_balances (account_id);
CREATE INDEX idx_outbox_unprocessed ON outbox_events (created_at ASC) WHERE processed = FALSE;


-- Spring Batch metadata tables (standard schema, batch.job.enabled=false,
-- batch.jdbc.initialize-schema=never means Flyway owns this, not Spring Batch)
CREATE TABLE BATCH_JOB_INSTANCE (
                                    JOB_INSTANCE_ID BIGINT PRIMARY KEY,
                                    VERSION BIGINT,
                                    JOB_NAME VARCHAR(100) NOT NULL,
                                    JOB_KEY VARCHAR(32) NOT NULL,
                                    CONSTRAINT JOB_INST_UN UNIQUE (JOB_NAME, JOB_KEY)
);

CREATE TABLE BATCH_JOB_EXECUTION (
                                     JOB_EXECUTION_ID BIGINT PRIMARY KEY,
                                     VERSION BIGINT,
                                     JOB_INSTANCE_ID BIGINT NOT NULL,
                                     CREATE_TIME TIMESTAMP NOT NULL,
                                     START_TIME TIMESTAMP DEFAULT NULL,
                                     END_TIME TIMESTAMP DEFAULT NULL,
                                     STATUS VARCHAR(10),
                                     EXIT_CODE VARCHAR(2500),
                                     EXIT_MESSAGE VARCHAR(2500),
                                     LAST_UPDATED TIMESTAMP,
                                     CONSTRAINT JOB_INST_EXEC_FK FOREIGN KEY (JOB_INSTANCE_ID)
                                         REFERENCES BATCH_JOB_INSTANCE (JOB_INSTANCE_ID)
);

CREATE TABLE BATCH_JOB_EXECUTION_PARAMS (
                                            JOB_EXECUTION_ID BIGINT NOT NULL,
                                            PARAMETER_NAME VARCHAR(100) NOT NULL,
                                            PARAMETER_TYPE VARCHAR(100) NOT NULL,
                                            PARAMETER_VALUE VARCHAR(2500),
                                            IDENTIFYING CHAR(1) NOT NULL,
                                            CONSTRAINT JOB_EXEC_PARAMS_FK FOREIGN KEY (JOB_EXECUTION_ID)
                                                REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
);

CREATE TABLE BATCH_STEP_EXECUTION (
                                      STEP_EXECUTION_ID BIGINT PRIMARY KEY,
                                      VERSION BIGINT NOT NULL,
                                      STEP_NAME VARCHAR(100) NOT NULL,
                                      JOB_EXECUTION_ID BIGINT NOT NULL,
                                      CREATE_TIME TIMESTAMP NOT NULL,
                                      START_TIME TIMESTAMP DEFAULT NULL,
                                      END_TIME TIMESTAMP DEFAULT NULL,
                                      STATUS VARCHAR(10),
                                      COMMIT_COUNT BIGINT,
                                      READ_COUNT BIGINT,
                                      FILTER_COUNT BIGINT,
                                      WRITE_COUNT BIGINT,
                                      READ_SKIP_COUNT BIGINT,
                                      WRITE_SKIP_COUNT BIGINT,
                                      PROCESS_SKIP_COUNT BIGINT,
                                      ROLLBACK_COUNT BIGINT,
                                      EXIT_CODE VARCHAR(2500),
                                      EXIT_MESSAGE VARCHAR(2500),
                                      LAST_UPDATED TIMESTAMP,
                                      CONSTRAINT JOB_EXEC_STEP_FK FOREIGN KEY (JOB_EXECUTION_ID)
                                          REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
);

CREATE TABLE BATCH_STEP_EXECUTION_CONTEXT (
                                              STEP_EXECUTION_ID BIGINT PRIMARY KEY,
                                              SHORT_CONTEXT VARCHAR(2500) NOT NULL,
                                              SERIALIZED_CONTEXT TEXT,
                                              CONSTRAINT STEP_EXEC_CTX_FK FOREIGN KEY (STEP_EXECUTION_ID)
                                                  REFERENCES BATCH_STEP_EXECUTION (STEP_EXECUTION_ID)
);

CREATE TABLE BATCH_JOB_EXECUTION_CONTEXT (
                                             JOB_EXECUTION_ID BIGINT PRIMARY KEY,
                                             SHORT_CONTEXT VARCHAR(2500) NOT NULL,
                                             SERIALIZED_CONTEXT TEXT,
                                             CONSTRAINT JOB_EXEC_CTX_FK FOREIGN KEY (JOB_EXECUTION_ID)
                                                 REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
);

CREATE SEQUENCE BATCH_STEP_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE BATCH_JOB_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE BATCH_JOB_SEQ MAXVALUE 9223372036854775807 NO CYCLE;