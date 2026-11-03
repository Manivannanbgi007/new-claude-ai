package com.example.productwrite.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

/**
 * Stores List<String> tags as one VARCHAR column: ["gaming","rtx"] <-> "gaming,rtx".
 * Safe because tags are validated to contain no commas (AppConstants.TAG_REGEX).
 */
@Converter
public class TagListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> tags) {
        return tags == null || tags.isEmpty() ? null : String.join(",", tags);
    }

    @Override
    public List<String> convertToEntityAttribute(String column) {
        return column == null || column.isBlank() ? List.of() : Arrays.asList(column.split(","));
    }
}
