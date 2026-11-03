package com.example.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class ChatClientConfig {

    /** Writes answers from retrieved passages (rules in prompts/rag-answer-prompt.txt). No tools, no memory. */
    @Bean
    public ChatClient ragAnswerChatClient(ChatClient.Builder builder,
                                          @Value("classpath:prompts/rag-answer-prompt.txt") Resource systemPrompt) {
        return builder.clone().defaultSystem(systemPrompt).build();
    }
}
