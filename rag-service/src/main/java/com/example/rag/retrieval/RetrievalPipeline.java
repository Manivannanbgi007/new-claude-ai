package com.example.rag.retrieval;

import com.example.rag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The retrieval pipeline, in order:
 *
 *   1. Hybrid search       vector + keyword (filters inside both) -> RRF fusion
 *   2. Metadata filtering  READY documents only, safety re-check, de-duplication
 *   3. Reranking           cross-encoder scores the best candidates
 *   4. Top-K               the K best chunks above the minimum relevance score
 */
@Service
public class RetrievalPipeline {

    private static final Logger log = LoggerFactory.getLogger(RetrievalPipeline.class);

    private final HybridSearchService hybridSearch;
    private final MetadataFilterService metadataFilter;
    private final RerankService rerankService;
    private final RagProperties.Retrieval settings;

    public RetrievalPipeline(HybridSearchService hybridSearch, MetadataFilterService metadataFilter,
                             RerankService rerankService, RagProperties properties) {
        this.hybridSearch = hybridSearch;
        this.metadataFilter = metadataFilter;
        this.rerankService = rerankService;
        this.settings = properties.retrieval();
    }

    public RetrievalResult retrieve(String query, SearchFilters filters, int topK) {
        long start = System.nanoTime();

        HybridSearchService.HybridResult hybrid = hybridSearch.search(query, filters);                 // 1
        List<ChunkCandidate> filtered = metadataFilter.apply(hybrid.fused(), filters);                // 2
        RerankService.RerankOutcome reranked = rerankService.rerank(query, filtered);                 // 3
        List<ChunkCandidate> top = reranked.ranked().stream()                                          // 4
                .filter(c -> !reranked.reranked() || c.getRerankScore() >= settings.minRerankScore())
                .limit(topK)
                .toList();

        long durationMs = (System.nanoTime() - start) / 1_000_000;
        log.info("Retrieval: vector={} keyword={} fused={} filtered={} reranked={}({}) top={} in {} ms",
                hybrid.vectorHits(), hybrid.keywordHits(), hybrid.fused().size(), filtered.size(),
                reranked.reranked(), reranked.rerankerName(), top.size(), durationMs);

        return new RetrievalResult(top, hybrid.vectorHits(), hybrid.keywordHits(), hybrid.fused().size(),
                filtered.size(), reranked.reranked(), reranked.rerankerName(), durationMs);
    }
}
