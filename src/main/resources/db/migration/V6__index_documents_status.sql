-- listing by status (pending / failed) is the common filter, newest first
CREATE INDEX ix_documents_status_created_at ON documents (status, created_at DESC);
