package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

/** app.rag.* in application.yml - every tuning value of the RAG pipeline in one place. */
@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(Ingestion ingestion, Retrieval retrieval, Reranker reranker) {

    public record Ingestion(
            int chunkSizeTokens,
            int minChunkSizeChars,
            DataSize maxFileSize,
            List<String> allowedExtensions,
            int workerThreads,
            int queueCapacity) {
    }

    public record Retrieval(
            int vectorCandidates,
            int keywordCandidates,
            double minSimilarity,
            int rrfK,
            int rerankCandidates,
            double minRerankScore,
            int defaultTopK,
            int maxTopK) {
    }

    /** type: cross-encoder | llm | none */
    public record Reranker(String type, String baseUrl, Duration timeout) {
    }
}
