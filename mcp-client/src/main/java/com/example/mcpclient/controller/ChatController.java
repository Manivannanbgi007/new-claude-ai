package com.example.mcpclient.controller;

import com.example.mcpclient.dto.ChatMessageRequest;
import com.example.mcpclient.dto.ChatMessageResponse;
import com.example.mcpclient.dto.ConversationMessage;
import com.example.mcpclient.service.ChatService;
import com.example.mcpclient.util.AppConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Chatbot REST API.
 * The @Pattern on path variables is checked by Spring MVC's built-in method
 * validation; invalid ids return 400 through GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** Send a message; returns the answer and the conversationId to continue with. */
    @PostMapping
    public ChatMessageResponse chat(@Valid @RequestBody ChatMessageRequest request) {
        return chatService.chat(request);
    }

    /** The stored history of a conversation (empty list if unknown). */
    @GetMapping("/{conversationId}/messages")
    public List<ConversationMessage> history(
            @PathVariable @Pattern(regexp = AppConstants.SAFE_ID_REGEX) String conversationId) {
        return chatService.getHistory(conversationId);
    }

    /** Forget a conversation ("start over"). */
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> clear(
            @PathVariable @Pattern(regexp = AppConstants.SAFE_ID_REGEX) String conversationId) {
        chatService.clearConversation(conversationId);
        return ResponseEntity.noContent().build();
    }
}
