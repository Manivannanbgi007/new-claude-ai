package com.example.rag.service;

import com.example.rag.dto.RagAnswerResponse;
import com.example.rag.dto.RagQueryRequest;
import com.example.rag.dto.RagSearchRequest;
import com.example.rag.dto.RagSearchResponse;

/** Questions against the knowledge base. */
public interface RagQueryService {

    /** Retrieval only: the top-K chunks (used by the MCP tool). */
    RagSearchResponse search(RagSearchRequest request);

    /** Retrieval + an answer with citations, written by rag-service. */
    RagAnswerResponse answer(RagQueryRequest request);
}
