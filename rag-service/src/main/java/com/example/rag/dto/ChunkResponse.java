package com.example.rag.dto;

/**
 * One retrieved chunk with everything needed for a citation.
 * score = rerank score (0..1) when reranked, otherwise the fused score.
 */
public record ChunkResponse(
        int rank,
        String chunkId,
        String documentId,
        String filename,
        Integer pageNumber,
        String category,
        String text,
        double score,
        Double vectorScore,
        Double keywordRank,
        Double rerankScore) {
}
