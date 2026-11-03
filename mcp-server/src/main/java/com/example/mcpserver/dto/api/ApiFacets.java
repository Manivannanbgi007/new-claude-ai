package com.example.mcpserver.dto.api;

import java.util.List;

public record ApiFacets(List<ApiFacetBucket> categories, List<ApiFacetBucket> brands) {
}
