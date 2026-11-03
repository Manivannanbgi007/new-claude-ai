package com.example.rag.retrieval;

import com.example.rag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * STAGE 3 - Reranking the best fused candidates.
 * If the reranker is unavailable (container down, timeout), the pipeline keeps working
 * with the fused order - search quality drops a little, but the service stays up.
 */
@Service
public class RerankService {

    private static final Logger log = LoggerFactory.getLogger(RerankService.class);

    public record RerankOutcome(List<ChunkCandidate> ranked, boolean reranked, String rerankerName) {
    }

    private final Reranker reranker;
    private final RagProperties.Retrieval settings;

    public RerankService(ObjectProvider<Reranker> reranker, RagProperties properties) {
        this.reranker = reranker.getIfAvailable();
        this.settings = properties.retrieval();
    }

    public RerankOutcome rerank(String query, List<ChunkCandidate> candidates) {
        List<ChunkCandidate> pool = new ArrayList<>(candidates.subList(0, Math.min(candidates.size(), settings.rerankCandidates())));
        if (reranker == null || pool.isEmpty()) {
            return new RerankOutcome(pool, false, "none");
        }
        try {
            List<Double> scores = reranker.score(query, pool.stream().map(ChunkCandidate::getText).toList());
            for (int i = 0; i < pool.size(); i++) {
                pool.get(i).setRerankScore(scores.get(i));
            }
            pool.sort(Comparator.comparingDouble(ChunkCandidate::finalScore).reversed());
            return new RerankOutcome(pool, true, reranker.name());
        } catch (RuntimeException ex) {
            log.warn("Reranker '{}' failed, using the fused order instead: {}", reranker.name(), ex.getMessage());
            return new RerankOutcome(pool, false, reranker.name() + " (unavailable)");
        }
    }
}
