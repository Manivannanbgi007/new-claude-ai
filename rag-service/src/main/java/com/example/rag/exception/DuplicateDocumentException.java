package com.example.rag.exception;

/** The same file (same SHA-256) was uploaded before. */
public class DuplicateDocumentException extends RuntimeException {

    private final String existingDocumentId;

    public DuplicateDocumentException(String existingDocumentId, String filename) {
        super("This file was already uploaded as '" + filename + "' (id " + existingDocumentId + ")");
        this.existingDocumentId = existingDocumentId;
    }

    public String getExistingDocumentId() {
        return existingDocumentId;
    }
}
