package com.example.mcpserver.mapper;

import com.example.mcpserver.dto.api.ApiFacetBucket;
import com.example.mcpserver.dto.api.ApiFacets;
import com.example.mcpserver.dto.api.ApiPage;
import com.example.mcpserver.dto.api.ApiProduct;
import com.example.mcpserver.dto.tool.FacetValue;
import com.example.mcpserver.dto.tool.ProductDetails;
import com.example.mcpserver.dto.tool.ProductSearchResult;
import com.example.mcpserver.dto.tool.ProductSummary;
import com.example.mcpserver.dto.tool.SearchFacets;
import org.springframework.stereotype.Component;

import java.util.List;

/** Converts product API responses into the compact shapes the LLM receives. */
@Component
public class ProductToolMapper {

    public ProductSearchResult toSearchResult(ApiPage<ApiProduct> page) {
        List<ProductSummary> products = page.content() == null ? List.of()
                : page.content().stream().map(this::toSummary).toList();

        Integer nextPage = page.last() ? null : page.page() + 1;
        return new ProductSearchResult(products, page.page(), page.size(), page.totalElements(),
                page.totalPages(), nextPage, messageFor(page, nextPage));
    }

    public ProductDetails toDetails(ApiProduct p) {
        return new ProductDetails(p.id(), p.sku(), p.name(), p.description(), p.brand(),
                p.category(), p.price(), p.ramGb(), p.rating(),
                p.tags() == null ? List.of() : p.tags());
    }

    public SearchFacets toFacets(ApiFacets facets) {
        return new SearchFacets(toValues(facets.categories()), toValues(facets.brands()));
    }

    private ProductSummary toSummary(ApiProduct p) {
        return new ProductSummary(p.id(), p.sku(), p.name(), p.brand(), p.category(), p.price(), p.ramGb(), p.rating());
    }

    private static List<FacetValue> toValues(List<ApiFacetBucket> buckets) {
        return buckets == null ? List.of()
                : buckets.stream().map(b -> new FacetValue(b.value(), b.count())).toList();
    }

    /** Plain-language hints help small models describe results and paging correctly. */
    private static String messageFor(ApiPage<ApiProduct> page, Integer nextPage) {
        if (page.totalElements() == 0) {
            return "No products matched. Suggest removing or relaxing a filter.";
        }
        String base = "Showing page " + page.page() + " of " + page.totalPages()
                + " (" + page.totalElements() + " matching products in total).";
        return nextPage == null
                ? base + " This is the last page."
                : base + " For more results call searchProducts again with the same arguments and page=" + nextPage + ".";
    }
}
