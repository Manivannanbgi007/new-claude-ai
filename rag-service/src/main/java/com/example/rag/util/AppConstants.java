package com.example.rag.util;

public final class AppConstants {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    public static final String UUID_REGEX = "^[0-9a-fA-F-]{36}$";
    /** Categories group documents for filtering: "policies", "manuals", "warranty" ... */
    public static final String CATEGORY_REGEX = "^[a-z0-9-]{1,50}$";
    public static final String DEFAULT_CATEGORY = "general";

    /** Metadata keys stored with every chunk in vector_store.metadata. */
    public static final String META_DOCUMENT_ID = "documentId";
    public static final String META_FILENAME = "filename";
    public static final String META_TITLE = "title";
    public static final String META_CATEGORY = "category";
    public static final String META_FILE_TYPE = "fileType";
    public static final String META_PAGE = "pageNumber";
    public static final String META_CHUNK_INDEX = "chunkIndex";

    private AppConstants() {
    }
}
