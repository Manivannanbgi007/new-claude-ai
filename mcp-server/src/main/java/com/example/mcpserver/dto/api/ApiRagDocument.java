package com.example.mcpserver.dto.api;

import java.time.Instant;

public record ApiRagDocument(String id, String filename, String title, String category, String fileType,
                             long sizeBytes, String status, int chunkCount, String errorMessage,
                             Instant createdAt, Instant updatedAt) {
}
