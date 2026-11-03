package com.example.mcpserver.dto.tool;

import java.util.List;

public record DocumentSearchResult(String question, List<DocumentPassage> passages, String message) {
}
