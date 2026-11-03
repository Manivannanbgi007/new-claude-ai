-- ---------------------------------------------------------------
-- Useful queries for the rag-service vector database (rag_db).
-- In pgAdmin: right-click rag_db -> Query Tool, paste a query, press F5.
-- ---------------------------------------------------------------

-- 1. Uploaded documents and their status
SELECT filename, title, category, status, chunk_count, created_at
FROM rag_document
ORDER BY created_at DESC;

-- 2. Chunks: which file, which page, the start of the text
SELECT metadata ->> 'filename'   AS file,
       metadata ->> 'pageNumber' AS page,
       metadata ->> 'chunkIndex' AS chunk,
       left(content, 80)         AS text_start
FROM vector_store
ORDER BY file, (metadata ->> 'chunkIndex')::int;

-- 3. Chunks per document (should match rag_document.chunk_count)
SELECT metadata ->> 'filename' AS file, count(*) AS chunks
FROM vector_store
GROUP BY 1
ORDER BY 1;

-- 4. What an embedding looks like: 768 numbers (only the first few shown)
SELECT metadata ->> 'filename'                  AS file,
       vector_dims(embedding)                   AS dimensions,
       left(embedding::text, 80) || ' ...'      AS embedding_start
FROM vector_store
LIMIT 3;

-- 5. KEYWORD leg of hybrid search, exactly like rag-service runs it
SELECT metadata ->> 'filename'            AS file,
       ts_rank_cd(content_tsv, q)          AS rank,
       left(content, 80)                   AS text_start
FROM vector_store, websearch_to_tsquery('english', 'E-4012') AS q
WHERE content_tsv @@ q
ORDER BY rank DESC;

-- 6. VECTOR leg without a query embedding: chunks most similar to chunk number 1
--    (1 - cosine distance = cosine similarity; the HNSW index makes this fast)
WITH first_chunk AS (SELECT embedding FROM vector_store LIMIT 1)
SELECT metadata ->> 'filename'                                   AS file,
       round((1 - (v.embedding <=> f.embedding))::numeric, 3)    AS similarity,
       left(content, 80)                                         AS text_start
FROM vector_store v, first_chunk f
ORDER BY v.embedding <=> f.embedding
LIMIT 5;

-- 7. Metadata filter, as used by searches with "category"
SELECT metadata ->> 'filename' AS file, left(content, 80) AS text_start
FROM vector_store
WHERE metadata ->> 'category' = 'shipping';

-- 8. Installed extensions (expect: vector, hstore, uuid-ossp)
SELECT extname, extversion FROM pg_extension ORDER BY extname;

-- 9. Indexes on the vector table (HNSW for vectors, GIN for full-text)
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'vector_store';
