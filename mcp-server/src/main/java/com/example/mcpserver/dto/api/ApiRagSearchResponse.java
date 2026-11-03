package com.example.mcpserver.dto.api;

import java.util.List;

public record ApiRagSearchResponse(String query, List<ApiRagChunk> chunks, ApiRagDiagnostics diagnostics) {
}
