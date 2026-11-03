package com.example.product.dto;

import java.util.List;

/**
 * Standard paginated response used by every service in the platform.
 * page is zero-based: page 0 is the first page.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / size);
        boolean last = page >= totalPages - 1;
        return new PageResponse<>(content, page, size, totalElements, totalPages, last);
    }
}
