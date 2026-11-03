package com.example.rag.dto;

import com.example.rag.util.AppConstants;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Optional metadata filters: only these documents / this category / this file type. */
public record RagSearchFilters(

        @Size(max = 20, message = "at most 20 documentIds")
        List<@Pattern(regexp = AppConstants.UUID_REGEX, message = "documentIds must be UUIDs") String> documentIds,

        @Pattern(regexp = AppConstants.CATEGORY_REGEX, message = "category: lower-case letters, digits and '-'")
        String category,

        @Pattern(regexp = "^(pdf|docx|pptx|txt|md|html)$", message = "fileType: pdf, docx, pptx, txt, md or html")
        String fileType) {
}
