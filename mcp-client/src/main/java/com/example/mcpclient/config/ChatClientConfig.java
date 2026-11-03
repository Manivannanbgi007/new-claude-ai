package com.example.mcpclient.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.Arrays;

/**
 * Two ChatClients that differ in ONE thing - the purchase tool:
 *
 *   enquiryChatClient   all MCP tools EXCEPT purchaseProduct  (default for every message)
 *   purchaseChatClient  all MCP tools                         (only when the user's message
 *                                                              is an explicit purchase request)
 *
 * ChatServiceImpl picks the client with PurchaseIntentDetector. Both share the same
 * system prompt and chat memory, so the conversation continues seamlessly
 * ("Is the MacBook Air available?" ... "Yes, buy it").
 */
@Configuration
public class ChatClientConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatClientConfig.class);

    /**
     * Name of the purchase tool on the MCP server (PurchaseTools.PURCHASE_TOOL_NAME).
     * Spring AI may prefix MCP tool names with the client/server name, so we match the end.
     */
    private static final String PURCHASE_TOOL_NAME = "purchaseProduct";

    @Bean
    public ChatClient enquiryChatClient(ChatClient.Builder builder, ToolCallbackProvider mcpToolCallbacks,
                                        ChatMemory conversationChatMemory,
                                        @Value("classpath:prompts/system-prompt.txt") Resource systemPrompt) {
        ToolCallback[] withoutPurchase = Arrays.stream(mcpToolCallbacks.getToolCallbacks())
                .filter(tool -> !isPurchaseTool(tool))
                .toArray(ToolCallback[]::new);
        log.info("Enquiry chat client tools: {}", names(withoutPurchase));
        return build(builder.clone(), withoutPurchase, conversationChatMemory, systemPrompt);
    }

    @Bean
    public ChatClient purchaseChatClient(ChatClient.Builder builder, ToolCallbackProvider mcpToolCallbacks,
                                         ChatMemory conversationChatMemory,
                                         @Value("classpath:prompts/system-prompt.txt") Resource systemPrompt) {
        ToolCallback[] allTools = mcpToolCallbacks.getToolCallbacks();
        if (Arrays.stream(allTools).noneMatch(ChatClientConfig::isPurchaseTool)) {
            log.warn("MCP server offers no '{}' tool - purchases will not work", PURCHASE_TOOL_NAME);
        }
        log.info("Purchase chat client tools: {}", names(allTools));
        return build(builder.clone(), allTools, conversationChatMemory, systemPrompt);
    }

    private static ChatClient build(ChatClient.Builder builder, ToolCallback[] tools, ChatMemory memory,
                                    Resource systemPrompt) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultToolCallbacks(tools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build(), new SimpleLoggerAdvisor())
                .build();
    }

    private static boolean isPurchaseTool(ToolCallback tool) {
        return tool.getToolDefinition().name().endsWith(PURCHASE_TOOL_NAME);
    }

    private static String names(ToolCallback[] tools) {
        return Arrays.toString(Arrays.stream(tools).map(t -> t.getToolDefinition().name()).toArray());
    }
}
