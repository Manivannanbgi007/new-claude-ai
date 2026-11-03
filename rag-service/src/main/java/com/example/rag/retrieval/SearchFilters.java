package com.example.rag.retrieval;

import java.util.List;

/**
 * Metadata filters of a search. All optional; null / empty = no restriction.
 * They are applied INSIDE both hybrid-search legs (not after), so a filter can never
 * "empty" a result list that was cut off before filtering.
 */
public record SearchFilters(List<String> documentIds, String category, String fileType) {

    public static SearchFilters none() {
        return new SearchFilters(List.of(), null, null);
    }

    public boolean hasDocumentIds() {
        return documentIds != null && !documentIds.isEmpty();
    }
}
