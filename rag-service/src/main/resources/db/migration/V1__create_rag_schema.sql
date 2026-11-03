-- ---------------------------------------------------------------
-- V1: rag-service schema (database rag_db, container postgres-rag).
-- ---------------------------------------------------------------
-- Extensions: vector = pgvector, hstore + uuid-ossp are used by Spring AI's PgVectorStore.
-- (The pgvector image's POSTGRES_USER is a superuser, so it may create extensions.
--  In production a DBA usually creates them once.)
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ---------------------------------------------------------------
-- The uploaded documents (one row per file). Chunks live in vector_store.
-- ---------------------------------------------------------------
CREATE TABLE rag_document
(
    id            VARCHAR(36)  NOT NULL,
    filename      VARCHAR(255) NOT NULL,
    title         VARCHAR(255) NOT NULL,
    category      VARCHAR(50)  NOT NULL,
    file_type     VARCHAR(10)  NOT NULL,
    content_type  VARCHAR(150) NULL,
    size_bytes    BIGINT       NOT NULL,
    -- SHA-256 of the file: the same file can't be uploaded twice
    sha256        VARCHAR(64)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    chunk_count   INT          NOT NULL DEFAULT 0,
    error_message VARCHAR(500) NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP(6) NOT NULL,
    updated_at    TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_rag_document PRIMARY KEY (id),
    CONSTRAINT uk_rag_document_sha256 UNIQUE (sha256),
    CONSTRAINT ck_rag_document_status CHECK (status IN ('PROCESSING', 'READY', 'FAILED'))
);

CREATE INDEX idx_rag_document_status ON rag_document (status);
CREATE INDEX idx_rag_document_category ON rag_document (category);

-- ---------------------------------------------------------------
-- Chunks + embeddings. Columns id/content/metadata/embedding are exactly what
-- Spring AI's PgVectorStore reads and writes. content_tsv is OUR addition for the
-- keyword leg of hybrid search: PostgreSQL keeps it up to date automatically.
-- ---------------------------------------------------------------
CREATE TABLE vector_store
(
    id          UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    content     TEXT,
    metadata    JSON,
    embedding   VECTOR(768),
    content_tsv TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', coalesce(content, ''))) STORED
);

-- Vector leg: approximate nearest neighbours by cosine distance
CREATE INDEX idx_vector_store_embedding ON vector_store USING hnsw (embedding vector_cosine_ops);
-- Keyword leg: full-text search
CREATE INDEX idx_vector_store_content_tsv ON vector_store USING gin (content_tsv);
-- Metadata filters and "delete all chunks of a document"
CREATE INDEX idx_vector_store_document_id ON vector_store ((metadata ->> 'documentId'));
CREATE INDEX idx_vector_store_category ON vector_store ((metadata ->> 'category'));
