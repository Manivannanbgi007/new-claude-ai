package com.example.mcpclient.service.impl;

import com.example.mcpclient.dto.ChatMessageRequest;
import com.example.mcpclient.dto.ChatMessageResponse;
import com.example.mcpclient.dto.ConversationMessage;
import com.example.mcpclient.exception.ChatProcessingException;
import com.example.mcpclient.mapper.ConversationMapper;
import com.example.mcpclient.service.ChatService;
import com.example.mcpclient.service.PurchaseIntentDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    private final ChatClient enquiryChatClient;
    private final ChatClient purchaseChatClient;
    private final PurchaseIntentDetector purchaseIntentDetector;
    private final ChatMemory chatMemory;
    private final ConversationMapper conversationMapper;

    public ChatServiceImpl(@Qualifier("enquiryChatClient") ChatClient enquiryChatClient,
                           @Qualifier("purchaseChatClient") ChatClient purchaseChatClient,
                           PurchaseIntentDetector purchaseIntentDetector,
                           ChatMemory conversationChatMemory,
                           ConversationMapper conversationMapper) {
        this.enquiryChatClient = enquiryChatClient;
        this.purchaseChatClient = purchaseChatClient;
        this.purchaseIntentDetector = purchaseIntentDetector;
        this.chatMemory = conversationChatMemory;
        this.conversationMapper = conversationMapper;
    }

    @Override
    public ChatMessageResponse chat(ChatMessageRequest request) {
        String conversationId = StringUtils.hasText(request.conversationId())
                ? request.conversationId()
                : UUID.randomUUID().toString();

        // The purchase tool is offered ONLY when the user's own words ask to buy.
        boolean purchaseRequest = purchaseIntentDetector.isPurchaseRequest(request.message());
        ChatClient chatClient = purchaseRequest ? purchaseChatClient : enquiryChatClient;

        log.info("Chat request conversationId={} purchaseIntent={} message=\"{}\"",
                conversationId, purchaseRequest, request.message());
        long start = System.nanoTime();

        // One call does everything: history, tool definitions, the tool-calling
        // loop with the MCP server, the final answer, and saving to memory.
        String answer = chatClient.prompt()
                .user(request.message())
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        long durationMs = (System.nanoTime() - start) / 1_000_000;

        if (!StringUtils.hasText(answer)) {
            throw new ChatProcessingException("The AI model returned an empty answer. Please rephrase your question.");
        }

        log.info("Chat answered conversationId={} in {} ms", conversationId, durationMs);
        return new ChatMessageResponse(conversationId, answer.trim(), durationMs, Instant.now());
    }

    @Override
    public List<ConversationMessage> getHistory(String conversationId) {
        return conversationMapper.toDto(chatMemory.get(conversationId));
    }

    @Override
    public void clearConversation(String conversationId) {
        chatMemory.clear(conversationId);
        log.info("Cleared conversation {}", conversationId);
    }
}
