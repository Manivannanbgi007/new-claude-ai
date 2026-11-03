package com.example.rag.ingestion;

import com.example.rag.repository.RagDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * At startup: documents still PROCESSING were interrupted by a shutdown or crash. Their
 * upload bytes are gone, so they can never finish - mark them FAILED so users re-upload.
 * (Partial chunks are harmless: MetadataFilterService only uses READY documents.)
 */
@Component
public class IngestionRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestionRecovery.class);

    private final RagDocumentRepository documentRepository;

    public IngestionRecovery(RagDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int interrupted = documentRepository.failAllProcessing("Processing was interrupted by a restart. Upload the file again.");
        if (interrupted > 0) {
            log.warn("{} document(s) were interrupted by a restart and marked FAILED", interrupted);
        }
    }
}
