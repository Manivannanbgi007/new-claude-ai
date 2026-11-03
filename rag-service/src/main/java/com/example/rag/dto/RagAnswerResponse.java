package com.example.rag.dto;

import java.util.List;

/** answered=false: the documents contain nothing relevant (no answer was invented). */
public record RagAnswerResponse(
        String question,
        String answer,
        boolean answered,
        List<SourceResponse> sources,
        RetrievalDiagnostics diagnostics,
        long totalMs) {
}
