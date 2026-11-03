package com.example.order.controller;

import com.example.order.dto.CatalogProductResponse;
import com.example.order.dto.PageResponse;
import com.example.order.service.ProductCatalogService;
import com.example.order.util.AppConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of order-service's LOCAL product catalogue - useful to check that
 * the product.catalog.v1 replication works. (Product search itself is product-service.)
 */
@RestController
@RequestMapping("/api/v1/orders/catalog")
public class OrderCatalogController {

    private final ProductCatalogService catalogService;

    public OrderCatalogController(ProductCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public PageResponse<CatalogProductResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(AppConstants.MAX_PAGE_SIZE) int size) {
        return catalogService.list(page, size);
    }

    @GetMapping("/{sku}")
    public CatalogProductResponse get(@PathVariable @Pattern(regexp = AppConstants.SKU_REGEX) String sku) {
        return catalogService.get(sku);
    }
}
