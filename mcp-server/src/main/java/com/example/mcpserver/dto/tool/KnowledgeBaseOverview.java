package com.example.mcpserver.dto.tool;

import java.util.List;

public record KnowledgeBaseOverview(List<KnowledgeDocument> documents, long totalDocuments, String message) {
}
