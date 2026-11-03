package com.example.mcpserver.service.impl;

import com.example.mcpserver.client.ProductApiClient;
import com.example.mcpserver.dto.tool.ProductDetails;
import com.example.mcpserver.dto.tool.ProductSearchResult;
import com.example.mcpserver.dto.tool.SearchFacets;
import com.example.mcpserver.exception.InvalidToolArgumentException;
import com.example.mcpserver.mapper.ProductToolMapper;
import com.example.mcpserver.model.ProductSearchParams;
import com.example.mcpserver.service.ProductCatalogService;
import com.example.mcpserver.util.AppConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cleans up LLM-provided arguments before they reach the product API.
 * LLMs often send "" or "null" instead of leaving a field out, use odd casing,
 * or ask for huge pages - all of that is normalised here.
 */
@Service
public class ProductCatalogServiceImpl implements ProductCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogServiceImpl.class);

    private static final Set<String> SORT_FIELDS = Set.of("relevance", "price", "name", "rating", "ram", "newest");
    private static final Set<String> SORT_DIRECTIONS = Set.of("asc", "desc");
    /** Values small models commonly send when they mean "no value". */
    private static final Set<String> EMPTY_MARKERS = Set.of("", "null", "none", "any", "all", "n/a");

    private final ProductApiClient productApiClient;
    private final ProductToolMapper mapper;

    public ProductCatalogServiceImpl(ProductApiClient productApiClient, ProductToolMapper mapper) {
        this.productApiClient = productApiClient;
        this.mapper = mapper;
    }

    @Override
    public ProductSearchResult search(ProductSearchParams params) {
        log.info("searchProducts arguments: {}", params);

        BigDecimal minPrice = nonNegative(params.minPrice(), "minPrice");
        BigDecimal maxPrice = nonNegative(params.maxPrice(), "maxPrice");
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new InvalidToolArgumentException("minPrice (" + minPrice + ") must not be greater than maxPrice ("
                    + maxPrice + "). For 'under X' use only maxPrice; for 'over X' use only minPrice.");
        }

        Integer minRam = positiveOrNull(params.minRamGb());
        Integer maxRam = positiveOrNull(params.maxRamGb());
        if (minRam != null && maxRam != null && minRam > maxRam) {
            throw new InvalidToolArgumentException("minRamGb must not be greater than maxRamGb. "
                    + "For exactly 16 GB use minRamGb=16 and maxRamGb=16.");
        }

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("q", clean(params.query()));
        query.put("category", lower(params.category()));
        query.put("brand", clean(params.brand()));
        query.put("minPrice", minPrice);
        query.put("maxPrice", maxPrice);
        query.put("minRamGb", minRam);
        query.put("maxRamGb", maxRam);
        query.put("sortBy", allowed(lower(params.sortBy()), SORT_FIELDS, "sortBy"));
        query.put("sortDirection", allowed(lower(params.sortDirection()), SORT_DIRECTIONS, "sortDirection"));
        query.put("page", pageOf(params.page()));
        query.put("size", sizeOf(params.size()));

        return mapper.toSearchResult(productApiClient.search(query));
    }

    @Override
    public ProductDetails getProduct(String id) {
        String cleanId = clean(id);
        if (cleanId == null) {
            throw new InvalidToolArgumentException(
                    "productId is required. Use the 'id' value from a searchProducts result.");
        }
        return mapper.toDetails(productApiClient.getById(cleanId));
    }

    @Override
    public SearchFacets getFacets() {
        return mapper.toFacets(productApiClient.getFacets());
    }

    // ---------------- argument clean-up helpers ----------------

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return EMPTY_MARKERS.contains(trimmed.toLowerCase(Locale.ROOT)) ? null : trimmed;
    }

    private static String lower(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toLowerCase(Locale.ROOT);
    }

    private static String allowed(String value, Set<String> allowedValues, String name) {
        if (value != null && !allowedValues.contains(value)) {
            throw new InvalidToolArgumentException("Invalid " + name + " '" + value
                    + "'. Allowed values: " + allowedValues + ", or leave it out.");
        }
        return value;
    }

    private static BigDecimal nonNegative(BigDecimal value, String name) {
        if (value != null && value.signum() < 0) {
            throw new InvalidToolArgumentException(name + " must not be negative.");
        }
        return value;
    }

    /** 0 or negative RAM means "not specified" to a small model more often than a real value. */
    private static Integer positiveOrNull(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    private static int pageOf(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private static int sizeOf(Integer size) {
        if (size == null || size <= 0) {
            return AppConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, AppConstants.MAX_PAGE_SIZE);
    }
}
