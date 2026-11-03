package com.example.mcpserver.tool;

import com.example.mcpserver.dto.tool.DocumentSearchResult;
import com.example.mcpserver.dto.tool.KnowledgeBaseOverview;
import com.example.mcpserver.service.KnowledgeBaseToolService;
import com.example.mcpserver.util.ToolCallContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Read-only tools for the uploaded documents (RAG). */
@Component
public class KnowledgeBaseTools {

    private final KnowledgeBaseToolService knowledgeBaseToolService;

    public KnowledgeBaseTools(KnowledgeBaseToolService knowledgeBaseToolService) {
        this.knowledgeBaseToolService = knowledgeBaseToolService;
    }

    @Tool(name = "searchDocuments", description = """
            Search the company's uploaded documents (policies, warranty, returns, shipping,
            manuals, troubleshooting, FAQs) and return the most relevant passages.
            Use it for questions about RULES, POLICIES or HOW-TO, e.g. "What is the return policy?",
            "How long is the warranty?", "What does error E-4012 mean?".
            Do NOT use it for product prices, product specs or stock - use the product tools for those.
            Answer ONLY from the returned passages and cite them as (source, page N).
            """)
    public DocumentSearchResult searchDocuments(
            @ToolParam(description = "The customer's question in their own words, e.g. 'return policy for opened headphones'")
            String question,
            @ToolParam(required = false, description = "Optional document category to search in, e.g. warranty, policies, shipping, support")
            String category) {
        return ToolCallContext.run("searchDocuments", () -> knowledgeBaseToolService.search(question, category));
    }

    @Tool(name = "listKnowledgeDocuments", description = """
            List the documents available in the knowledge base (title, filename, category).
            Use it for "Which documents do you have?" or to find a category for searchDocuments.
            """)
    public KnowledgeBaseOverview listKnowledgeDocuments() {
        return ToolCallContext.run("listKnowledgeDocuments", knowledgeBaseToolService::overview);
    }
}
