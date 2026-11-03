package com.example.rag.retrieval;

import com.example.rag.config.RagProperties;
import com.example.rag.repository.ChunkRepository;
import com.example.rag.util.AppConstants;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * STAGE 1 - Hybrid search. Two complementary searches, then Reciprocal Rank Fusion:
 *
 *   vector leg   finds chunks with the same MEANING ("refund" ~ "money back")
 *   keyword leg  finds chunks with the exact WORDS  (model numbers, SKUs, error codes)
 *
 * RRF: score = sum over both lists of 1 / (k + rank). It only uses the POSITIONS in each
 * list, so the very different score scales of the two legs never have to be compared.
 * A chunk found by both legs rises to the top.
 */
@Service
public class HybridSearchService {

    private final VectorStore vectorStore;
    private final ChunkRepository chunkRepository;
    private final RagProperties.Retrieval settings;

    public HybridSearchService(VectorStore vectorStore, ChunkRepository chunkRepository, RagProperties properties) {
        this.vectorStore = vectorStore;
        this.chunkRepository = chunkRepository;
        this.settings = properties.retrieval();
    }

    public record HybridResult(List<ChunkCandidate> fused, int vectorHits, int keywordHits) {
    }

    public HybridResult search(String query, SearchFilters filters) {
        List<ChunkCandidate> vectorHits = vectorSearch(query, filters);
        List<ChunkCandidate> keywordHits = chunkRepository.keywordSearch(query, filters, settings.keywordCandidates());
        return new HybridResult(fuse(vectorHits, keywordHits), vectorHits.size(), keywordHits.size());
    }

    private List<ChunkCandidate> vectorSearch(String query, SearchFilters filters) {
        SearchRequest.Builder request = SearchRequest.builder()
                .query(query)
                .topK(settings.vectorCandidates())
                .similarityThreshold(settings.minSimilarity());
        Filter.Expression filter = toFilterExpression(filters);
        if (filter != null) {
            request.filterExpression(filter);
        }
        List<Document> documents = vectorStore.similaritySearch(request.build());
        List<ChunkCandidate> hits = new ArrayList<>();
        for (Document document : documents == null ? List.<Document>of() : documents) {
            Map<String, Object> m = document.getMetadata();
            ChunkCandidate candidate = new ChunkCandidate(document.getId(), document.getText(),
                    string(m.get(AppConstants.META_DOCUMENT_ID)), string(m.get(AppConstants.META_FILENAME)),
                    string(m.get(AppConstants.META_CATEGORY)), string(m.get(AppConstants.META_FILE_TYPE)),
                    integer(m.get(AppConstants.META_PAGE)), integer(m.get(AppConstants.META_CHUNK_INDEX)));
            candidate.setVectorScore(document.getScore());
            hits.add(candidate);
        }
        return hits;
    }

    /** The same metadata filters as the keyword leg, in Spring AI's portable filter language. */
    private static Filter.Expression toFilterExpression(SearchFilters filters) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> conditions = new ArrayList<>();
        if (filters.hasDocumentIds()) {
            conditions.add(b.in(AppConstants.META_DOCUMENT_ID, filters.documentIds().toArray()));
        }
        if (filters.category() != null) {
            conditions.add(b.eq(AppConstants.META_CATEGORY, filters.category()));
        }
        if (filters.fileType() != null) {
            conditions.add(b.eq(AppConstants.META_FILE_TYPE, filters.fileType()));
        }
        if (conditions.isEmpty()) {
            return null;
        }
        FilterExpressionBuilder.Op combined = conditions.getFirst();
        for (int i = 1; i < conditions.size(); i++) {
            combined = b.and(combined, conditions.get(i));
        }
        return combined.build();
    }

    /** Reciprocal Rank Fusion of the two ranked lists (rank 1 = best). */
    private List<ChunkCandidate> fuse(List<ChunkCandidate> vectorHits, List<ChunkCandidate> keywordHits) {
        Map<String, ChunkCandidate> byId = new LinkedHashMap<>();
        addRanked(byId, vectorHits);
        for (int rank = 0; rank < keywordHits.size(); rank++) {
            ChunkCandidate hit = keywordHits.get(rank);
            ChunkCandidate existing = byId.get(hit.getChunkId());
            if (existing == null) {
                hit.setFusedScore(rrf(rank));
                byId.put(hit.getChunkId(), hit);
            } else {
                existing.setKeywordRank(hit.getKeywordRank());
                existing.setFusedScore(existing.getFusedScore() + rrf(rank));
            }
        }
        List<ChunkCandidate> fused = new ArrayList<>(byId.values());
        fused.sort(Comparator.comparingDouble(ChunkCandidate::getFusedScore).reversed());
        return fused;
    }

    private void addRanked(Map<String, ChunkCandidate> byId, List<ChunkCandidate> hits) {
        for (int rank = 0; rank < hits.size(); rank++) {
            ChunkCandidate hit = hits.get(rank);
            hit.setFusedScore(rrf(rank));
            byId.put(hit.getChunkId(), hit);
        }
    }

    private double rrf(int zeroBasedRank) {
        return 1.0 / (settings.rrfK() + zeroBasedRank + 1);
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : (int) Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
