package com.example.mcpclient.dto;

import java.time.Instant;

/** The assistant's answer. Send conversationId with the next message to continue. */
public record ChatMessageResponse(
        String conversationId,
        String answer,
        long durationMs,
        Instant timestamp) {
}
