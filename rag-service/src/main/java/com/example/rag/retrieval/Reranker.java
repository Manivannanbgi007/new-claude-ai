package com.example.rag.retrieval;

import java.util.List;

/**
 * Scores how well each text answers the query: 0 (irrelevant) .. 1 (perfect match).
 * Unlike the vector search, a reranker reads query AND chunk TOGETHER, which makes it
 * far more precise - and slower, so it only runs on the ~20 best fused candidates.
 */
public interface Reranker {

    /** @return one score per text, in the same order as the texts */
    List<Double> score(String query, List<String> texts);

    String name();
}
