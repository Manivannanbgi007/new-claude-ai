package com.example.mcpclient.dto;

import com.example.mcpclient.util.AppConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One user message.
 * conversationId is optional: leave it out to start a new conversation, then send
 * back the id from the response to continue it ("show the next page").
 */
public record ChatMessageRequest(

        @Pattern(regexp = AppConstants.SAFE_ID_REGEX,
                message = "conversationId may contain only letters, digits and '-' (max 64)")
        String conversationId,

        @NotBlank(message = "message is required")
        @Size(max = 2000, message = "message must be at most 2000 characters")
        String message) {
}
