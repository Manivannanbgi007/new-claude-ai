package com.example.rag.ingestion;

import com.example.rag.config.RagProperties;
import com.example.rag.util.AppConstants;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * File bytes -> text -> chunks with metadata.
 *
 *   PDF   PagePdfDocumentReader: one Document per page, so answers can cite "page 4"
 *   other TikaDocumentReader:    DOCX, PPTX, HTML, TXT, MD ...
 *
 * Chunks are ~400 tokens (TokenTextSplitter). Every chunk carries the metadata used later
 * for filtering and citations: documentId, filename, title, category, fileType, pageNumber,
 * chunkIndex.
 */
@Component
public class DocumentParser {

    private final TokenTextSplitter splitter;

    public DocumentParser(RagProperties properties) {
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.ingestion().chunkSizeTokens())
                .withMinChunkSizeChars(properties.ingestion().minChunkSizeChars())
                .withMinChunkLengthToEmbed(10)
                .withMaxNumChunks(10_000)
                .withKeepSeparator(true)
                .build();
    }

    public record DocumentInfo(String documentId, String filename, String title, String category, String fileType) {
    }

    public List<Document> parse(byte[] content, DocumentInfo info) {
        Resource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return info.filename();   // lets Tika detect the format from the extension too
            }
        };

        List<Document> pages = "pdf".equals(info.fileType())
                ? new PagePdfDocumentReader(resource, PdfDocumentReaderConfig.builder().withPagesPerDocument(1).build()).get()
                : new TikaDocumentReader(resource).get();

        List<Document> chunks = new ArrayList<>();
        int chunkIndex = 0;
        for (Document page : pages) {
            if (page.getText() == null || page.getText().isBlank()) {
                continue;   // empty page (e.g. only an image)
            }
            Integer pageNumber = pageNumber(page.getMetadata());
            for (Document piece : splitter.split(List.of(page))) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put(AppConstants.META_DOCUMENT_ID, info.documentId());
                metadata.put(AppConstants.META_FILENAME, info.filename());
                metadata.put(AppConstants.META_TITLE, info.title());
                metadata.put(AppConstants.META_CATEGORY, info.category());
                metadata.put(AppConstants.META_FILE_TYPE, info.fileType());
                metadata.put(AppConstants.META_CHUNK_INDEX, chunkIndex++);
                if (pageNumber != null) {
                    metadata.put(AppConstants.META_PAGE, pageNumber);
                }
                chunks.add(new Document(piece.getText(), metadata));
            }
        }
        return chunks;
    }

    /** PagePdfDocumentReader stores the page number under the key "page_number". */
    private static Integer pageNumber(Map<String, Object> metadata) {
        Object value = metadata.get("page_number");
        return value instanceof Number number ? number.intValue() : null;
    }
}
