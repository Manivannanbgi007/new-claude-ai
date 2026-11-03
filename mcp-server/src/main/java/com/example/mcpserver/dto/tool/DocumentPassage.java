package com.example.mcpserver.dto.tool;

/** One retrieved passage. source + page are what the answer must cite, e.g. "shipping-faq.pdf, page 2". */
public record DocumentPassage(int number, String source, Integer page, String category, String text, double relevance) {
}
