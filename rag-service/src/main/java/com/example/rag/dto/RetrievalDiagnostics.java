package com.example.rag.dto;

/** What each pipeline stage did - useful for tuning and for learning how RAG behaves. */
public record RetrievalDiagnostics(
        int vectorHits,
        int keywordHits,
        int afterFusion,
        int afterMetadataFilter,
        boolean reranked,
        String reranker,
        int returned,
        long retrievalMs) {
}
