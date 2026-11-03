package com.example.rag.exception;

/** Empty file, unsupported type, too large, invalid category ... */
public class InvalidDocumentException extends RuntimeException {

    public InvalidDocumentException(String message) {
        super(message);
    }
}
