package com.example.mcpserver.dto.api;

import java.util.List;

public record ApiRagFilters(List<String> documentIds, String category, String fileType) {
}
