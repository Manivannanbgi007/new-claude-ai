package com.example.mcpserver.dto.api;

import java.util.List;

/** The platform's standard paginated response. */
public record ApiPage<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {
}
