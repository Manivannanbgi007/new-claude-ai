package com.example.rag.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** One uploaded file (table "rag_document"). Its chunks are stored in vector_store. */
@Entity
@Table(name = "rag_document")
public class RagDocument {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "file_type", nullable = false, length = 10)
    private String fileType;

    @Column(name = "content_type", length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Required by JPA. */
    protected RagDocument() {
    }

    public RagDocument(String id, String filename, String title, String category, String fileType,
                       String contentType, long sizeBytes, String sha256) {
        this.id = id;
        this.filename = filename;
        this.title = title;
        this.category = category;
        this.fileType = fileType;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.status = DocumentStatus.PROCESSING;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void markReady(int chunks) {
        status = DocumentStatus.READY;
        chunkCount = chunks;
        errorMessage = null;
    }

    public void markFailed(String reason) {
        status = DocumentStatus.FAILED;
        chunkCount = 0;
        errorMessage = reason == null ? "Unknown error" : reason.substring(0, Math.min(reason.length(), 500));
    }

    public String getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public String getFileType() {
        return fileType;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
