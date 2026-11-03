package com.example.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * RAG service: upload documents, index them (pgvector + full-text), retrieve the most
 * relevant chunks (hybrid search -> metadata filtering -> reranking -> top-K) and answer
 * questions from them with the local Ollama model.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RagServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagServiceApplication.class, args);
    }
}
