package com.example.rag.controller;

import com.example.rag.dto.RagAnswerResponse;
import com.example.rag.dto.RagQueryRequest;
import com.example.rag.dto.RagSearchRequest;
import com.example.rag.dto.RagSearchResponse;
import com.example.rag.service.RagQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *   POST /api/v1/rag/search  retrieval only -> top-K chunks with scores and citations
 *   POST /api/v1/rag/query   retrieval + answer written from those chunks
 */
@RestController
@RequestMapping("/api/v1/rag")
public class RagController {

    private final RagQueryService ragQueryService;

    public RagController(RagQueryService ragQueryService) {
        this.ragQueryService = ragQueryService;
    }

    @PostMapping("/search")
    public RagSearchResponse search(@Valid @RequestBody RagSearchRequest request) {
        return ragQueryService.search(request);
    }

    @PostMapping("/query")
    public RagAnswerResponse query(@Valid @RequestBody RagQueryRequest request) {
        return ragQueryService.answer(request);
    }
}
