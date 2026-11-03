package com.example.mcpclient.dto;

/** One stored message of a conversation: role is "user" or "assistant". */
public record ConversationMessage(String role, String content) {
}
