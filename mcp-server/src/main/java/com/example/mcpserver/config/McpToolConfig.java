package com.example.mcpserver.config;

import com.example.mcpserver.tool.KnowledgeBaseTools;
import com.example.mcpserver.tool.OrderTools;
import com.example.mcpserver.tool.ProductTools;
import com.example.mcpserver.tool.PurchaseTools;
import com.example.mcpserver.tool.StockTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the @Tool methods with the MCP server.
 * The MCP auto-configuration publishes every ToolCallbackProvider bean as MCP tools.
 * A new tool class is simply added to toolObjects(...).
 */
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider platformToolCallbacks(ProductTools productTools, StockTools stockTools,
                                                      OrderTools orderTools, PurchaseTools purchaseTools,
                                                      KnowledgeBaseTools knowledgeBaseTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(productTools, stockTools, orderTools, purchaseTools, knowledgeBaseTools)
                .build();
    }
}
