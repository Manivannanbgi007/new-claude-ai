# RAG Service

A document knowledge base. Users upload documents (PDF, DOCX, PPTX, TXT, MD, HTML), and the chatbot answers
questions from them, with citations.

| | |
|---|---|
| Port | `8093` (through the gateway: `/api/v1/rag/**`) |
| Database | PostgreSQL 17 + **pgvector** (container `postgres-rag`, localhost:**5433**, db `rag_db`) |
| Models (Ollama) | `nomic-embed-text` (embeddings, 768 dims), `qwen2.5:7b` (answers) |
| Reranker | `BAAI/bge-reranker-base` cross-encoder (container `reranker`, localhost:8095) |
| Registers with | Eureka (`rag-service`) |

---

## 1. How it fits in

```
Chat UI ─► Gateway ─► mcp-client ─► MCP Server ─► searchDocuments tool ─► Gateway ─► rag-service
Chat UI "Documents" panel ─────────────────────────────────────────────────► Gateway ─► rag-service
```

rag-service is a plain REST microservice. It knows nothing about MCP, so any client can use it (web, mobile,
other services). The MCP Server's `searchDocuments` tool calls `POST /api/v1/rag/search`, and the chat LLM
writes the answer from the returned passages.

---

## 2. Ingestion: upload → searchable chunks

```
POST /documents ─► validate (type, size, category) ─► SHA-256 duplicate check ─► rag_document (PROCESSING)
                                                                                     │ 202 Accepted
   background thread (ingestionExecutor, 2 workers):
   parse (PDF: per page | Tika: others) ─► split (~400 tokens) ─► add metadata ─► embed (Ollama)
   ─► store in vector_store (pgvector) ─► READY   (any error ─► chunks removed ─► FAILED + reason)
```

Every chunk carries metadata for filtering and citations: `documentId, filename, title, category, fileType,
pageNumber, chunkIndex`.

| Design choice | Why |
|---|---|
| **Asynchronous** ingestion, 202 + polling | Embedding a large PDF on CPU can take minutes; the upload request returns immediately |
| **Bounded** worker pool (2 threads, queue 50) | Embedding competes with the chat model for CPU; a full queue answers 503 instead of overloading |
| **PDF per page** | Answers can cite "page 2" |
| **SHA-256 duplicate check** | The same file uploaded twice would double every search hit |
| **Startup recovery** (`IngestionRecovery`) | Documents interrupted by a restart are marked FAILED instead of hanging in PROCESSING |

---

## 3. Retrieval: Hybrid Search → Metadata Filtering → Reranking → Top-K

```
query ─┬─ vector leg   pgvector HNSW, cosine similarity ≥ 0.30, top 20   ┐  filters applied
       └─ keyword leg  PostgreSQL full-text (websearch_to_tsquery), top 20 ┘  INSIDE both legs
                 │
          RRF fusion (k = 60)                                   ─► STAGE 1: HybridSearchService
                 │
          READY docs only, safety re-check, de-duplicate        ─► STAGE 2: MetadataFilterService
                 │
          cross-encoder scores the best 20 (query + chunk)      ─► STAGE 3: RerankService
                 │
          best K (default 5) with rerank score ≥ 0.10           ─► STAGE 4: RetrievalPipeline
```

| Stage | What it adds |
|---|---|
| **Vector leg** | Finds meaning: "money back" matches "refund" |
| **Keyword leg** | Finds exact words the embedding can't: error codes (`E-4012`), model numbers, SKUs |
| **RRF fusion** | Merges both by *rank position* (score = Σ 1/(60 + rank)), so their incomparable score scales never mix. Chunks found by both legs rise to the top. |
| **Metadata filtering** | `category` / `documentIds` / `fileType` run **inside** both queries (filtering after the search could leave nothing). Stage 2 adds rules that can't be pushed down: only READY documents, and no duplicates. |
| **Reranking** | A cross-encoder reads query and chunk **together**, which is far more precise than comparing two separate vectors. It runs only on ~20 candidates, because it's slower. |
| **Top-K + min score** | Keeps the context small and drops irrelevant chunks, which lets the service say "not in the documents" instead of guessing |

**Reranker options** (`app.rag.reranker.type`):

| Value | Needs | Quality / speed |
|---|---|---|
| `cross-encoder` (default) | `reranker` container | Best precision, ~100 ms on CPU |
| `llm` | Ollama only | One extra chat call; slower and less precise |
| `none` | – | Fused order only |

If the reranker is unreachable, the search still works: it logs a warning and returns the fused order
(`diagnostics.reranked: false`).

Every value in this section is set in `application.yml` under `app.rag.retrieval`.

---

## 4. API

| Method | URL | Purpose | Success |
|---|---|---|---|
| POST | `/api/v1/rag/documents` | Upload (`multipart/form-data`: `file`, optional `category`, `title`) | **202** PROCESSING |
| GET | `/api/v1/rag/documents?status=&category=&page=&size=` | List documents | 200 |
| GET | `/api/v1/rag/documents/{id}` | Status of one document | 200 |
| DELETE | `/api/v1/rag/documents/{id}` | Remove the document **and its chunks** | 204 |
| POST | `/api/v1/rag/search` | Retrieval only: top-K chunks + `diagnostics` | 200 |
| POST | `/api/v1/rag/query` | Retrieval + answer with numbered citations | 200 |

**Search response (shortened):**

```json
{
  "query": "E-4012",
  "chunks": [
    { "rank": 1, "filename": "troubleshooting-guide.md", "pageNumber": null, "category": "support",
      "text": "Error E-4012 appears when a laptop does not recognise ...",
      "score": 0.9731, "vectorScore": 0.41, "keywordRank": 0.1, "rerankScore": 0.9731 }
  ],
  "diagnostics": { "vectorHits": 4, "keywordHits": 1, "afterFusion": 4, "afterMetadataFilter": 4,
                   "reranked": true, "reranker": "cross-encoder", "returned": 1, "retrievalMs": 142 }
}
```

`diagnostics` shows what each stage did, which is the fastest way to understand and tune retrieval.

---

## 5. Setup

1. **Infrastructure** (infrastructure-v10 adds `postgres-rag`, `reranker`, and the embedding model):
   ```powershell
   cd product-platform\infrastructure
   docker compose up -d
   docker compose logs -f ollama-model-init   # pulls nomic-embed-text (≈270 MB)
   docker compose logs -f reranker            # first start downloads the reranker model (≈1 GB)
   ```
   Wait until the reranker log shows `Ready`, and `docker compose ps postgres-rag` shows `healthy`.
2. **Project:** generate a skeleton on start.spring.io (Boot 4.0.8, Maven, Java 21, YAML, Artifact
   `rag-service`, Package `com.example.rag`) with **Spring Web, Validation, Spring Data JPA, Flyway Migration,
   PostgreSQL Driver, Ollama, PGvector Vector Database, Actuator, Eureka Discovery Client**. Copy `pom.xml`,
   `src`, `http`, and `samples` over it. Use the same `spring-ai.version` as mcp-server.
3. **Run:**
   ```powershell
   cd product-platform\rag-service
   .\mvnw.cmd clean spring-boot:run
   ```
   Expect `Successfully applied 1 migration to schema "public"` and `Tomcat started on port 8093`.
4. **Gateway:** use api-gateway-v3 (adds the `rag-service` route with a 120 s timeout).
5. **MCP:** use mcp-server-v4 (tools `searchDocuments`, `listKnowledgeDocuments`) and mcp-client-v5 (prompt +
   Documents panel). Restart **mcp-server before mcp-client**.

---

## 6. Test

1. `http/rag-service.http`, top to bottom: upload the 4 sample documents, wait for READY, then run the searches
   and questions. The key checks:
   - **#7:** meaning without matching words (vector leg).
   - **#8:** `E-4012` (keyword leg).
   - **#9:** category filter.
   - **#12:** a PDF answer with `pageNumber: 2`.
   - **#13 / #18:** `answered: false`, meaning no invented answers.
2. **Chat** at http://localhost:8080/chat/:
   - **Documents** → upload a sample file → wait for *Ready*.
   - Ask *"What is the return policy for opened headphones?"*: the answer comes from the document, with a
     citation.
   - Ask *"Is the MacBook Air in stock and how long is its warranty?"*: the bot calls **two** tools (stock and
     documents) and combines the answers.
   - Ask *"I want to buy the MacBook Air"*: the purchase workflow works as before.

**Look at the data:**

```powershell
docker exec -it postgres-rag psql -U rag_user -d rag_db -c "SELECT filename, status, chunk_count FROM rag_document;"
docker exec -it postgres-rag psql -U rag_user -d rag_db -c "SELECT metadata->>'filename' AS file, metadata->>'pageNumber' AS page, left(content, 60) FROM vector_store;"
```

---

## 7. Security and production notes

- **Prompt injection:** documents are untrusted data. Both the answer prompt and the chat prompt say passages
  are data, not instructions, and document tools are **read-only**: a malicious document can't trigger a
  purchase, because the purchase tool is only offered for the user's own explicit purchase request.
- **File handling:** only the file *name* is kept (client paths are stripped), types are whitelisted, and size
  is limited in Spring (20 MB) and in the UI.
- **Access control:** there is no authentication yet. With security, upload and delete should require an admin
  role, and the gateway should restrict `/api/v1/rag/documents` writes.
- **Original files are not stored**, only the chunks. To re-index with a new embedding model, upload the files
  again. Storing originals (e.g. in object storage) is the next step if re-indexing becomes routine.
- **Changing the embedding model** changes the vector size and meaning. It needs a new migration
  (`VECTOR(n)`), a matching `dimensions` setting, and a full re-upload.
- **Scanned PDFs** (images only) contain no text; they are marked FAILED with a hint. OCR would be a
  separate ingestion step.

---

## 8. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Document stays PROCESSING for a long time | Large file on CPU, or Ollama busy | Check the rag-service log; wait, or use smaller files |
| FAILED: "model ... not found" | Embedding model not pulled | `docker exec -it ollama ollama pull nomic-embed-text` |
| Startup: `expected 768 dimensions, not ...` | Different embedding model | Use `nomic-embed-text`, or change `VECTOR(768)` with a new migration and set `dimensions` |
| `diagnostics.reranked: false`, reranker "(unavailable)" | Reranker container not ready | `docker compose logs reranker`; the first start downloads the model |
| Search finds nothing for an exact code | Document not READY, or wrong category filter | Check `GET /documents`; remove the filter |
| Answers ignore the documents | The chat model answered without the tool | Check the mcp-server log for `searchDocuments`; ask more specifically ("according to the return policy...") |
| 413 on upload | File larger than 20 MB | Split the document, or raise both `spring.servlet.multipart.*` and `app.rag.ingestion.max-file-size` |
| Documents panel: "document service was not found" | Chat opened on port 8091 | Open http://localhost:8080/chat/ (through the gateway) |
| Flyway: `permission denied to create extension "vector"` | DB user is not a superuser | Create the extensions once as a superuser, then restart |
