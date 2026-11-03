package com.example.mcpserver.tool;

import com.example.mcpserver.dto.tool.StockCheckResult;
import com.example.mcpserver.service.StockToolService;
import com.example.mcpserver.util.ToolCallContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Read-only stock information. Checking stock NEVER reserves or orders anything. */
@Component
public class StockTools {

    private final StockToolService stockToolService;

    public StockTools(StockToolService stockToolService) {
        this.stockToolService = stockToolService;
    }

    @Tool(name = "checkProductStock", description = """
            Check whether a product is in stock and how many units are available right now.
            READ-ONLY: it never reserves stock and never creates an order.
            Use it for questions like "Is the MacBook Air available?" or "Do you have 3 of these?".
            Use the 'sku' value from a searchProducts result.
            """)
    public StockCheckResult checkProductStock(
            @ToolParam(description = "Product SKU exactly as returned by searchProducts, e.g. LAP-APL-001")
            String sku,
            @ToolParam(required = false, description = "How many units the user asks about, 1 to 10. Default 1")
            Integer quantity) {
        return ToolCallContext.run("checkProductStock", () -> stockToolService.check(sku, quantity));
    }
}
