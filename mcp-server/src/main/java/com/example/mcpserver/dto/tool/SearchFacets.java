package com.example.mcpserver.dto.tool;

import java.util.List;

/** Valid category and brand values, so the LLM can use exact filter values. */
public record SearchFacets(List<FacetValue> categories, List<FacetValue> brands) {
}
