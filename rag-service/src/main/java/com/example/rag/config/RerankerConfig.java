package com.example.rag.config;

import com.example.rag.retrieval.CrossEncoderReranker;
import com.example.rag.retrieval.LlmReranker;
import com.example.rag.retrieval.Reranker;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the reranker from app.rag.reranker.type (application.yml):
 *   cross-encoder (default)  |  llm  |  none (no bean -> reranking is skipped)
 */
@Configuration
public class RerankerConfig {

    @Bean
    @ConditionalOnProperty(name = "app.rag.reranker.type", havingValue = "cross-encoder", matchIfMissing = true)
    public Reranker crossEncoderReranker(RagProperties properties) {
        return new CrossEncoderReranker(properties.reranker());
    }

    @Bean
    @ConditionalOnProperty(name = "app.rag.reranker.type", havingValue = "llm")
    public Reranker llmReranker(ChatClient.Builder builder) {
        return new LlmReranker(builder);
    }
}
