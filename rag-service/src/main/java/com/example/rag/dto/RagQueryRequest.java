package com.example.rag.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/v1/rag/query - retrieval + an answer written by rag-service itself. */
public record RagQueryRequest(

        @NotBlank(message = "question is required")
        @Size(max = 1000, message = "question must be at most 1000 characters")
        String question,

        @Min(value = 1, message = "topK must be at least 1")
        @Max(value = 10, message = "topK must be at most 10")
        Integer topK,

        @Valid
        RagSearchFilters filters) {
}
