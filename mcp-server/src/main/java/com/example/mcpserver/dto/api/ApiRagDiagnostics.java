package com.example.mcpserver.dto.api;

public record ApiRagDiagnostics(int vectorHits, int keywordHits, int afterFusion, int afterMetadataFilter,
                                boolean reranked, String reranker, int returned, long retrievalMs) {
}
