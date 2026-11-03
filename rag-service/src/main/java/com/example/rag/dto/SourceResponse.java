package com.example.rag.dto;

/** A source cited in an answer: [1] handbook.pdf, page 4. */
public record SourceResponse(int number, String documentId, String filename, Integer pageNumber, String excerpt) {
}
