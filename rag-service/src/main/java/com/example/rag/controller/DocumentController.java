package com.example.rag.controller;

import com.example.rag.dto.DocumentResponse;
import com.example.rag.dto.PageResponse;
import com.example.rag.model.DocumentStatus;
import com.example.rag.service.DocumentService;
import com.example.rag.util.AppConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/** Knowledge-base documents: upload, list, get, delete. */
@RestController
@RequestMapping("/api/v1/rag/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * multipart/form-data with a "file" part, plus optional "category" and "title".
     * 202 Accepted: the file is stored as PROCESSING and indexed in the background;
     * poll GET /{id} until status is READY (or FAILED with errorMessage).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) @Size(max = 50) String category,
            @RequestParam(required = false) @Size(max = 255) String title) {
        DocumentResponse document = documentService.upload(file, category, title);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(document.id()).toUri();
        return ResponseEntity.accepted().location(location).body(document);
    }

    /** Newest first. Optional filters: status (processing, ready, failed) and category. */
    @GetMapping
    public PageResponse<DocumentResponse> list(
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) @Pattern(regexp = AppConstants.CATEGORY_REGEX) String category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return documentService.list(status, category, page, size);
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable @Pattern(regexp = AppConstants.UUID_REGEX) String id) {
        return documentService.get(id);
    }

    /** Removes the document and all of its chunks from the knowledge base. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Pattern(regexp = AppConstants.UUID_REGEX) String id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
