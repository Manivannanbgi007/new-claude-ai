package com.example.mcpserver.dto.api;

public record ApiRagChunk(
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
