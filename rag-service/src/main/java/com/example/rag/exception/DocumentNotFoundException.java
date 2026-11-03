package com.example.rag.exception;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String id) {
        super("Document with id '" + id + "' was not found");
    }
}
