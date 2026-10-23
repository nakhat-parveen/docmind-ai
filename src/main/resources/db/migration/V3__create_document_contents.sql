CREATE TABLE document_contents
(
    document_id UUID PRIMARY KEY REFERENCES documents (id) ON DELETE CASCADE,
    data        BYTEA NOT NULL
);
