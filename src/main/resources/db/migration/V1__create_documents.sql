CREATE TABLE documents
(
    id            UUID PRIMARY KEY,
    filename      VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    checksum      VARCHAR(64)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    chunk_count   INTEGER      NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX ux_documents_checksum ON documents (checksum);
CREATE INDEX ix_documents_created_at ON documents (created_at DESC);
