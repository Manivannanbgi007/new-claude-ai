package com.example.mcpclient.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryConfig {

    /**
     * Keeps the last N messages of each conversation.
     * The repository is Spring AI's auto-configured in-memory store:
     * simple for development, but history is lost on restart
     * (see "Production notes" in the phase explanation).
     */
    @Bean
    public ChatMemory conversationChatMemory(ChatMemoryRepository chatMemoryRepository,
                                             @Value("${app.chat.memory-max-messages:20}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(maxMessages)
                .build();
    }
}
