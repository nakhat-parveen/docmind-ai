CREATE TABLE outbox_events
(
    id             UUID PRIMARY KEY,
    document_id    UUID         NOT NULL,
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL,
    sent_at        TIMESTAMPTZ,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     VARCHAR(1000)
);

-- the relay only ever looks for events that were not sent yet
CREATE INDEX ix_outbox_events_unsent ON outbox_events (created_at) WHERE sent_at IS NULL;
