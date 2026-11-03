package com.example.mcpserver.dto.tool;

import java.util.List;

/**
 * Result of the searchProducts tool.
 * nextPage is null on the last page; message gives the LLM a plain-language hint.
 */
public record ProductSearchResult(
        List<ProductSummary> products,
        int page,
        int pageSize,
        long totalMatches,
        int totalPages,
        Integer nextPage,
        String message) {
}
