package com.example.rag.retrieval;

import java.util.List;

/** The top-K chunks plus numbers that show what each pipeline stage did. */
public record RetrievalResult(
        List<ChunkCandidate> chunks,
        int vectorHits,
        int keywordHits,
        int afterFusion,
        int afterMetadataFilter,
        boolean reranked,
        String reranker,
        long durationMs) {
}
