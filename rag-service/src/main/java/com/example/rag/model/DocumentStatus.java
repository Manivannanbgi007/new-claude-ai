package com.example.rag.model;

/**
 * PROCESSING -> READY   (chunks embedded and searchable)
 * PROCESSING -> FAILED  (see errorMessage; upload the file again)
 * Only READY documents are ever used to answer questions.
 */
public enum DocumentStatus {
    PROCESSING,
    READY,
    FAILED
}
