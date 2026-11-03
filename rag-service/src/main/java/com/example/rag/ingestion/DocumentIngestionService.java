package com.example.rag.ingestion;

import com.example.rag.model.RagDocument;
import com.example.rag.repository.ChunkRepository;
import com.example.rag.repository.RagDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Background ingestion of one uploaded document:
 *   parse -> chunk -> embed (Ollama, nomic-embed-text) -> store in pgvector -> READY
 * On any error: remove partial chunks and mark the document FAILED with the reason.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final DocumentParser parser;
    private final VectorStore vectorStore;
    private final ChunkRepository chunkRepository;
    private final RagDocumentRepository documentRepository;

    public DocumentIngestionService(DocumentParser parser, VectorStore vectorStore, ChunkRepository chunkRepository,
                                    RagDocumentRepository documentRepository) {
        this.parser = parser;
        this.vectorStore = vectorStore;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
    }

    @Async("ingestionExecutor")
    public void ingest(String documentId, byte[] content) {
        RagDocument document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            return;   // deleted before ingestion started
        }
        long start = System.nanoTime();
        try {
            List<Document> chunks = parser.parse(content, new DocumentParser.DocumentInfo(document.getId(),
                    document.getFilename(), document.getTitle(), document.getCategory(), document.getFileType()));
            if (chunks.isEmpty()) {
                fail(documentId, "No text found in the file (scanned image PDFs need OCR first).");
                return;
            }
            vectorStore.add(chunks);   // embeds every chunk and inserts it into vector_store

            RagDocument current = documentRepository.findById(documentId).orElse(null);
            if (current == null) {
                chunkRepository.deleteByDocumentId(documentId);   // deleted while we were embedding
                return;
            }
            current.markReady(chunks.size());
            documentRepository.save(current);
            log.info("Document {} ({}) ready: {} chunks in {} ms", documentId, document.getFilename(),
                    chunks.size(), (System.nanoTime() - start) / 1_000_000);
        } catch (RuntimeException ex) {
            log.error("Ingestion of document {} failed", documentId, ex);
            fail(documentId, ex.getMessage());
        }
    }

    private void fail(String documentId, String reason) {
        chunkRepository.deleteByDocumentId(documentId);
        documentRepository.findById(documentId).ifPresent(d -> {
            d.markFailed(reason);
            documentRepository.save(d);
        });
    }
}
