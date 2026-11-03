package com.example.rag.dto;

import com.example.rag.model.DocumentStatus;

import java.time.Instant;

/** An uploaded document. Poll GET /api/v1/rag/documents/{id} until status is READY. */
public record DocumentResponse(
        String id,
        String filename,
        String title,
        String category,
        String fileType,
        long sizeBytes,
        DocumentStatus status,
        int chunkCount,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {
}
