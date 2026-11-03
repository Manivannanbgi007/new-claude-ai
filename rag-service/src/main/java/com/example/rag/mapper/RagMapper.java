package com.example.rag.mapper;

import com.example.rag.dto.ChunkResponse;
import com.example.rag.dto.DocumentResponse;
import com.example.rag.dto.RagSearchFilters;
import com.example.rag.dto.RetrievalDiagnostics;
import com.example.rag.dto.SourceResponse;
import com.example.rag.model.RagDocument;
import com.example.rag.retrieval.ChunkCandidate;
import com.example.rag.retrieval.RetrievalResult;
import com.example.rag.retrieval.SearchFilters;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Component
public class RagMapper {

    private static final int EXCERPT_LENGTH = 240;

    public DocumentResponse toResponse(RagDocument d) {
        return new DocumentResponse(d.getId(), d.getFilename(), d.getTitle(), d.getCategory(), d.getFileType(),
                d.getSizeBytes(), d.getStatus(), d.getChunkCount(), d.getErrorMessage(),
                toInstant(d.getCreatedAt()), toInstant(d.getUpdatedAt()));
    }

    public SearchFilters toFilters(RagSearchFilters f) {
        if (f == null) {
            return SearchFilters.none();
        }
        return new SearchFilters(
                f.documentIds() == null ? List.of() : f.documentIds().stream().map(id -> id.toLowerCase(Locale.ROOT)).toList(),
                StringUtils.hasText(f.category()) ? f.category() : null,
                StringUtils.hasText(f.fileType()) ? f.fileType() : null);
    }

    public List<ChunkResponse> toChunks(List<ChunkCandidate> chunks) {
        return java.util.stream.IntStream.range(0, chunks.size())
                .mapToObj(i -> {
                    ChunkCandidate c = chunks.get(i);
                    return new ChunkResponse(i + 1, c.getChunkId(), c.getDocumentId(), c.getFilename(), c.getPageNumber(),
                            c.getCategory(), c.getText(), round(c.finalScore()), c.getVectorScore(), c.getKeywordRank(),
                            c.getRerankScore());
                })
                .toList();
    }

    public List<SourceResponse> toSources(List<ChunkCandidate> chunks) {
        return java.util.stream.IntStream.range(0, chunks.size())
                .mapToObj(i -> {
                    ChunkCandidate c = chunks.get(i);
                    String text = c.getText().replaceAll("\\s+", " ").trim();
                    String excerpt = text.length() <= EXCERPT_LENGTH ? text : text.substring(0, EXCERPT_LENGTH) + "...";
                    return new SourceResponse(i + 1, c.getDocumentId(), c.getFilename(), c.getPageNumber(), excerpt);
                })
                .toList();
    }

    public RetrievalDiagnostics toDiagnostics(RetrievalResult r) {
        return new RetrievalDiagnostics(r.vectorHits(), r.keywordHits(), r.afterFusion(), r.afterMetadataFilter(),
                r.reranked(), r.reranker(), r.chunks().size(), r.durationMs());
    }

    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private static Instant toInstant(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
    }
}
