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

CREATE INDEX idx_outbox_unprocessed ON outbox_events (created_at ASC) WHERE processed = FALSE;