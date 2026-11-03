package com.example.mcpserver.tool;

import com.example.mcpserver.dto.tool.PurchaseResult;
import com.example.mcpserver.service.PurchaseWorkflowService;
import com.example.mcpserver.util.ToolCallContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The ONLY tool that changes data: it places a real order.
 *
 * Two independent safety nets:
 *  1. mcp-client offers this tool to the LLM ONLY when the user's own message expresses
 *     explicit purchase intent (PurchaseIntentDetector). For enquiries it is not available.
 *  2. The workflow itself never orders a product that is missing or out of stock.
 *
 * The tool name "purchaseProduct" is referenced by mcp-client - keep them in sync.
 */
@Component
public class PurchaseTools {

    public static final String PURCHASE_TOOL_NAME = "purchaseProduct";

    private final PurchaseWorkflowService purchaseWorkflowService;

    public PurchaseTools(PurchaseWorkflowService purchaseWorkflowService) {
        this.purchaseWorkflowService = purchaseWorkflowService;
    }

    @Tool(name = PURCHASE_TOOL_NAME, description = """
            Buy a product: checks the product and its stock, then places an order.
            ONLY call this when the user EXPLICITLY asks to buy, purchase or order a product,
            e.g. "I want to buy the MacBook Air" or "Order two AirPods for me".
            NEVER call it for questions or browsing ("Is it available?", "How much is it?",
            "Show me laptops") - use searchProducts or checkProductStock for those.
            Call it only ONCE per purchase request.
            Pass productName as the customer named the product (e.g. "MacBook Air 13").
            The tool finds the product itself; you do NOT need to know its id.
            If the result reason is AMBIGUOUS_PRODUCT, ask the customer which candidate they mean.
            Result outcome: ORDER_CONFIRMED, ORDER_REJECTED, ORDER_PENDING, or NOT_PURCHASED
            (no order created - tell the user the reason).
            """)
    public PurchaseResult purchaseProduct(
            @ToolParam(description = "Name of the product to buy, as the customer said it, e.g. 'MacBook Air 13'")
            String productName,
            @ToolParam(required = false, description = "Only if you got it from searchProducts in THIS reply: the product 'id'. "
                    + "Never guess it and never pass a SKU here")
            String productId,
            @ToolParam(required = false, description = "Number of units to buy, 1 to 10. Default 1")
            Integer quantity) {
        return ToolCallContext.run(PURCHASE_TOOL_NAME,
                () -> purchaseWorkflowService.purchase(productName, productId, quantity));
    }
}
