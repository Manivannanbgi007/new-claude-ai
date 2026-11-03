package com.example.mcpserver.tool;

import com.example.mcpserver.dto.tool.ProductDetails;
import com.example.mcpserver.dto.tool.ProductSearchResult;
import com.example.mcpserver.dto.tool.SearchFacets;
import com.example.mcpserver.model.ProductSearchParams;
import com.example.mcpserver.service.ProductCatalogService;
import com.example.mcpserver.util.ToolCallContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The MCP tools. This class plays the role a controller plays in a REST API:
 * it defines the "endpoints" (tools) and delegates all work to the service.
 *
 * The descriptions are NOT comments for developers - they are sent to the LLM
 * and are the main thing that decides whether it calls the right tool with the
 * right arguments. Examples in descriptions help small local models a lot.
 *
 * All tools are read-only on purpose: the chatbot can search, but never create,
 * change or delete products.
 */
@Component
public class ProductTools {

    private final ProductCatalogService catalogService;

    public ProductTools(ProductCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Tool(name = "searchProducts", description = """
            Search the product catalogue with optional text search, filters, sorting and paging.
            All arguments are optional; leave out any argument you do not need.
            Prices are in USD. Pages start at 0.
            Examples:
            - "laptops under $1000"            -> category="laptop", maxPrice=1000
            - "products from Apple"            -> brand="Apple"
            - "products with 16 GB RAM"        -> minRamGb=16, maxRamGb=16
            - "cheapest laptops"               -> category="laptop", sortBy="price", sortDirection="asc"
            - "products containing 'gaming'"   -> query="gaming"
            - "next page"                      -> repeat the previous call with page = previous page + 1
            The result contains nextPage (null when there are no more pages).
            Each product has an 'id' (for getProductById / purchaseProduct) and a 'sku' (for checkProductStock).
            """)
    public ProductSearchResult searchProducts(
            @ToolParam(required = false, description = "Free-text search in name, brand, tags and description, e.g. 'gaming' or 'noise cancelling'")
            String query,
            @ToolParam(required = false, description = "Category, lowercase singular: laptop, smartphone, tablet, headphones or monitor. Call getSearchFacets for the full list")
            String category,
            @ToolParam(required = false, description = "Brand name, e.g. Apple, Dell, Samsung (case does not matter)")
            String brand,
            @ToolParam(required = false, description = "Minimum price in USD, inclusive")
            BigDecimal minPrice,
            @ToolParam(required = false, description = "Maximum price in USD, inclusive. Use for 'under' or 'below' a price")
            BigDecimal maxPrice,
            @ToolParam(required = false, description = "Minimum RAM in GB, inclusive")
            Integer minRamGb,
            @ToolParam(required = false, description = "Maximum RAM in GB, inclusive. For an exact amount set minRamGb and maxRamGb to the same value")
            Integer maxRamGb,
            @ToolParam(required = false, description = "Sort field: relevance, price, name, rating, ram or newest. Use price for cheapest/most expensive")
            String sortBy,
            @ToolParam(required = false, description = "asc (lowest first) or desc (highest first). Cheapest = price asc")
            String sortDirection,
            @ToolParam(required = false, description = "Page number starting at 0")
            Integer page,
            @ToolParam(required = false, description = "Results per page, 1 to 20. Default 10")
            Integer size) {

        return ToolCallContext.run("searchProducts", () -> catalogService.search(new ProductSearchParams(
                query, category, brand, minPrice, maxPrice, minRamGb, maxRamGb,
                sortBy, sortDirection, page, size)));
    }

    @Tool(name = "getProductById", description = """
            Get full details of one product, including its description and tags.
            Use the 'id' value from a searchProducts result.
            """)
    public ProductDetails getProductById(
            @ToolParam(description = "The product id, exactly as returned by searchProducts")
            String productId) {

        return ToolCallContext.run("getProductById", () -> catalogService.getProduct(productId));
    }

    @Tool(name = "getSearchFacets", description = """
            List all product categories and brands that exist, with product counts.
            Use it to find valid values for the category and brand filters of searchProducts,
            or to answer questions like "which brands do you sell?".
            """)
    public SearchFacets getSearchFacets() {
        return ToolCallContext.run("getSearchFacets", catalogService::getFacets);
    }
}
