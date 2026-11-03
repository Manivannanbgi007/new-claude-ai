package com.example.rag.service.impl;

import com.example.rag.config.RagProperties;
import com.example.rag.dto.RagAnswerResponse;
import com.example.rag.dto.RagQueryRequest;
import com.example.rag.dto.RagSearchRequest;
import com.example.rag.dto.RagSearchResponse;
import com.example.rag.mapper.RagMapper;
import com.example.rag.retrieval.ChunkCandidate;
import com.example.rag.retrieval.RetrievalPipeline;
import com.example.rag.retrieval.RetrievalResult;
import com.example.rag.service.RagQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagQueryServiceImpl implements RagQueryService {

    private static final Logger log = LoggerFactory.getLogger(RagQueryServiceImpl.class);
    private static final String NOT_FOUND_ANSWER = "The uploaded documents don't contain information about this.";

    private final RetrievalPipeline pipeline;
    private final ChatClient chatClient;
    private final RagMapper mapper;
    private final RagProperties.Retrieval settings;

    public RagQueryServiceImpl(RetrievalPipeline pipeline, @Qualifier("ragAnswerChatClient") ChatClient chatClient,
                               RagMapper mapper, RagProperties properties) {
        this.pipeline = pipeline;
        this.chatClient = chatClient;
        this.mapper = mapper;
        this.settings = properties.retrieval();
    }

    @Override
    public RagSearchResponse search(RagSearchRequest request) {
        RetrievalResult result = pipeline.retrieve(request.query().trim(), mapper.toFilters(request.filters()), topK(request.topK()));
        return new RagSearchResponse(request.query(), mapper.toChunks(result.chunks()), mapper.toDiagnostics(result));
    }

    /**
     * Retrieval-augmented generation: the model sees ONLY the retrieved passages.
     * No relevant passages -> a fixed "not found" answer WITHOUT calling the model,
     * so it has no chance to invent one.
     */
    @Override
    public RagAnswerResponse answer(RagQueryRequest request) {
        long start = System.nanoTime();
        String question = request.question().trim();
        RetrievalResult result = pipeline.retrieve(question, mapper.toFilters(request.filters()), topK(request.topK()));

        if (result.chunks().isEmpty()) {
            return new RagAnswerResponse(question, NOT_FOUND_ANSWER, false, List.of(),
                    mapper.toDiagnostics(result), elapsedMs(start));
        }
        String answer = chatClient.prompt().user(buildPrompt(question, result.chunks())).call().content();
        boolean answered = answer != null && !answer.contains(NOT_FOUND_ANSWER);
        log.info("RAG answer for \"{}\" from {} chunks in {} ms", question, result.chunks().size(), elapsedMs(start));
        return new RagAnswerResponse(question, answer == null ? NOT_FOUND_ANSWER : answer.trim(), answered,
                mapper.toSources(result.chunks()), mapper.toDiagnostics(result), elapsedMs(start));
    }

    private static String buildPrompt(String question, List<ChunkCandidate> chunks) {
        StringBuilder prompt = new StringBuilder("Context passages:\n\n");
        for (int i = 0; i < chunks.size(); i++) {
            ChunkCandidate c = chunks.get(i);
            prompt.append("[").append(i + 1).append("] (").append(c.getFilename());
            if (c.getPageNumber() != null) {
                prompt.append(", page ").append(c.getPageNumber());
            }
            prompt.append(")\n").append(c.getText().trim()).append("\n\n");
        }
        prompt.append("Question: ").append(question);
        return prompt.toString();
    }

    private int topK(Integer requested) {
        int value = requested == null ? settings.defaultTopK() : requested;
        return Math.max(1, Math.min(value, settings.maxTopK()));
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
