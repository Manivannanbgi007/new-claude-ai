package com.example.mcpserver.service;

import com.example.mcpserver.dto.tool.DocumentSearchResult;
import com.example.mcpserver.dto.tool.KnowledgeBaseOverview;

/** Read-only access to the document knowledge base (rag-service). */
public interface KnowledgeBaseToolService {

    DocumentSearchResult search(String question, String category);

    KnowledgeBaseOverview overview();
}
