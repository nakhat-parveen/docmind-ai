CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Layout expected by Spring AI's PgVectorStore. The dimension depends on the
-- embedding model, so it is injected per profile via a Flyway placeholder.
CREATE TABLE vector_store
(
    id        UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    content   TEXT,
    metadata  JSON,
    embedding VECTOR(${embedding_dimensions})
);

CREATE INDEX ix_vector_store_embedding ON vector_store USING hnsw (embedding vector_cosine_ops);
CREATE INDEX ix_vector_store_document_id ON vector_store ((metadata ->> 'document_id'));
