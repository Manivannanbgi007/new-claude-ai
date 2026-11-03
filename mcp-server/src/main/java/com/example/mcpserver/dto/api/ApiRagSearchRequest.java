package com.example.mcpserver.dto.api;

/** Body of rag-service POST /api/v1/rag/search. */
public record ApiRagSearchRequest(String query, Integer topK, ApiRagFilters filters) {
}
