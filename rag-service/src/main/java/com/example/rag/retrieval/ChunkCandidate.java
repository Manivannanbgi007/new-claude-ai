package com.example.rag.retrieval;

/**
 * One chunk moving through the retrieval pipeline. Each stage fills in its own score:
 *   vectorScore  cosine similarity from the vector leg (null = not found by that leg)
 *   keywordRank  full-text rank from the keyword leg     (null = not found by that leg)
 *   fusedScore   Reciprocal Rank Fusion of both legs
 *   rerankScore  cross-encoder relevance 0..1            (null = not reranked)
 */
public class ChunkCandidate {

    private final String chunkId;
    private final String text;
    private final String documentId;
    private final String filename;
    private final String category;
    private final String fileType;
    private final Integer pageNumber;
    private final Integer chunkIndex;

    private Double vectorScore;
    private Double keywordRank;
    private double fusedScore;
    private Double rerankScore;

    public ChunkCandidate(String chunkId, String text, String documentId, String filename, String category,
                          String fileType, Integer pageNumber, Integer chunkIndex) {
        this.chunkId = chunkId;
        this.text = text;
        this.documentId = documentId;
        this.filename = filename;
        this.category = category;
        this.fileType = fileType;
        this.pageNumber = pageNumber;
        this.chunkIndex = chunkIndex;
    }

    /** The score used for the FINAL order: rerank score when available, fused score otherwise. */
    public double finalScore() {
        return rerankScore != null ? rerankScore : fusedScore;
    }

    public String getChunkId() {
        return chunkId;
    }

    public String getText() {
        return text;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getFilename() {
        return filename;
    }

    public String getCategory() {
        return category;
    }

    public String getFileType() {
        return fileType;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public Double getVectorScore() {
        return vectorScore;
    }

    public void setVectorScore(Double vectorScore) {
        this.vectorScore = vectorScore;
    }

    public Double getKeywordRank() {
        return keywordRank;
    }

    public void setKeywordRank(Double keywordRank) {
        this.keywordRank = keywordRank;
    }

    public double getFusedScore() {
        return fusedScore;
    }

    public void setFusedScore(double fusedScore) {
        this.fusedScore = fusedScore;
    }

    public Double getRerankScore() {
        return rerankScore;
    }

    public void setRerankScore(Double rerankScore) {
        this.rerankScore = rerankScore;
    }
}
