package com.example.rag.service;

import com.example.rag.dto.DocumentResponse;
import com.example.rag.dto.PageResponse;
import com.example.rag.model.DocumentStatus;
import org.springframework.web.multipart.MultipartFile;

/** Managing the knowledge base: upload, list, get, delete documents. */
public interface DocumentService {

    /** Registers the file (PROCESSING) and starts background ingestion. */
    DocumentResponse upload(MultipartFile file, String category, String title);

    PageResponse<DocumentResponse> list(DocumentStatus status, String category, int page, int size);

    DocumentResponse get(String id);

    /** Removes the document AND all its chunks. */
    void delete(String id);
}
