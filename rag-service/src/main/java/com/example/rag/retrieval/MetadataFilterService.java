package com.example.rag.retrieval;

import com.example.rag.repository.RagDocumentRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * STAGE 2 - Metadata filtering: rules that can't (or shouldn't) be pushed into the search
 * queries. The request filters themselves already ran INSIDE both hybrid-search legs.
 *   - only chunks of READY documents (a document being ingested or just deleted may
 *     still have chunks in the index for a moment)
 *   - the request filters once more, as a safety net
 *   - no duplicates: the same text uploaded in two files is shown once
 */
@Service
public class MetadataFilterService {

    private final RagDocumentRepository documentRepository;

    public MetadataFilterService(RagDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public List<ChunkCandidate> apply(List<ChunkCandidate> candidates, SearchFilters filters) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        Set<String> documentIds = new HashSet<>();
        candidates.forEach(c -> documentIds.add(c.getDocumentId()));
        Set<String> ready = new HashSet<>(documentRepository.findReadyIds(documentIds));

        Set<String> seenTexts = new HashSet<>();
        List<ChunkCandidate> kept = new ArrayList<>();
        for (ChunkCandidate c : candidates) {
            if (!ready.contains(c.getDocumentId()) || !matches(c, filters)) {
                continue;
            }
            if (seenTexts.add(normalize(c.getText()))) {
                kept.add(c);
            }
        }
        return kept;
    }

    private static boolean matches(ChunkCandidate c, SearchFilters filters) {
        return (!filters.hasDocumentIds() || filters.documentIds().contains(c.getDocumentId()))
                && (filters.category() == null || filters.category().equals(c.getCategory()))
                && (filters.fileType() == null || filters.fileType().equals(c.getFileType()));
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
