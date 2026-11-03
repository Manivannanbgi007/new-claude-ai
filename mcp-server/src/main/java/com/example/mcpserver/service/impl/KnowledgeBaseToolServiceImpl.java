package com.example.mcpserver.service.impl;

import com.example.mcpserver.client.RagApiClient;
import com.example.mcpserver.dto.api.ApiPage;
import com.example.mcpserver.dto.api.ApiRagChunk;
import com.example.mcpserver.dto.api.ApiRagDocument;
import com.example.mcpserver.dto.api.ApiRagFilters;
import com.example.mcpserver.dto.api.ApiRagSearchRequest;
import com.example.mcpserver.dto.api.ApiRagSearchResponse;
import com.example.mcpserver.dto.tool.DocumentPassage;
import com.example.mcpserver.dto.tool.DocumentSearchResult;
import com.example.mcpserver.dto.tool.KnowledgeBaseOverview;
import com.example.mcpserver.dto.tool.KnowledgeDocument;
import com.example.mcpserver.exception.InvalidToolArgumentException;
import com.example.mcpserver.service.KnowledgeBaseToolService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Orchestrates rag-service: the retrieval pipeline (hybrid search, metadata filtering,
 * reranking, top-K) runs THERE. This class only shapes the result for the chat LLM,
 * which then writes the answer and cites the passages.
 */
@Service
public class KnowledgeBaseToolServiceImpl implements KnowledgeBaseToolService {

    private static final int TOP_K = 5;
    /** Keeps the chat model's context small: ~5 passages x 1500 characters. */
    private static final int MAX_PASSAGE_CHARS = 1500;
    /** Values small models send when they mean "no filter". */
    private static final Set<String> NO_FILTER = Set.of("", "null", "none", "any", "all");

    private final RagApiClient ragApiClient;

    public KnowledgeBaseToolServiceImpl(RagApiClient ragApiClient) {
        this.ragApiClient = ragApiClient;
    }

    @Override
    public DocumentSearchResult search(String question, String category) {
        if (!StringUtils.hasText(question)) {
            throw new InvalidToolArgumentException("question is required: what the customer wants to know.");
        }
        String categoryFilter = normalizeCategory(category);
        ApiRagSearchResponse response = ragApiClient.search(new ApiRagSearchRequest(question.trim(), TOP_K,
                categoryFilter == null ? null : new ApiRagFilters(null, categoryFilter, null)));

        List<ApiRagChunk> chunks = response.chunks() == null ? List.of() : response.chunks();
        List<DocumentPassage> passages = chunks.stream()
                .map(c -> new DocumentPassage(c.rank(), c.filename(), c.pageNumber(), c.category(),
                        truncate(c.text()), Math.round(c.score() * 100.0) / 100.0))
                .toList();

        String message = passages.isEmpty()
                ? "No relevant passages found in the uploaded documents. Tell the customer the documents "
                + "don't contain this information - do NOT answer from general knowledge."
                : "Answer ONLY from these passages and cite each fact as (source, page N).";
        return new DocumentSearchResult(question, passages, message);
    }

    @Override
    public KnowledgeBaseOverview overview() {
        ApiPage<ApiRagDocument> page = ragApiClient.listReadyDocuments(50);
        List<KnowledgeDocument> documents = page.content() == null ? List.of() : page.content().stream()
                .map(d -> new KnowledgeDocument(d.title(), d.filename(), d.category()))
                .toList();
        String message = documents.isEmpty()
                ? "The knowledge base is empty. Documents can be uploaded in the chat UI (Documents) or via the API."
                : documents.size() + " document(s) available.";
        return new KnowledgeBaseOverview(documents, page.totalElements(), message);
    }

    private static String normalizeCategory(String category) {
        if (category == null || NO_FILTER.contains(category.trim().toLowerCase(Locale.ROOT))) {
            return null;
        }
        String value = category.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("^[a-z0-9-]{1,50}$")) {
            throw new InvalidToolArgumentException("category must be a single lower-case word, e.g. 'warranty'.");
        }
        return value;
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() <= MAX_PASSAGE_CHARS ? clean : clean.substring(0, MAX_PASSAGE_CHARS) + "...";
    }
}
