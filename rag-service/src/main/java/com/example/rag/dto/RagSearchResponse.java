package com.example.rag.dto;

import java.util.List;

public record RagSearchResponse(String query, List<ChunkResponse> chunks, RetrievalDiagnostics diagnostics) {
}
