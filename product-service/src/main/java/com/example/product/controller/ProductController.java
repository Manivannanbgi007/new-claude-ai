package com.example.product.controller;

import com.example.product.dto.FacetResponse;
import com.example.product.dto.PageResponse;
import com.example.product.dto.ProductResponse;
import com.example.product.dto.ProductSearchRequest;
import com.example.product.service.ProductSearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * READ SIDE of the product module: search, filter, sort, page, facets, get by id.
 * Writes (POST/PUT/DELETE) are served by product-write-service; the gateway routes
 * them there. This service changes the index ONLY through the CDC projection.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductSearchService productSearchService;

    public ProductController(ProductSearchService productSearchService) {
        this.productSearchService = productSearchService;
    }

    /** Search, filter, sort and page products. All query parameters are optional. */
    @GetMapping
    public PageResponse<ProductResponse> search(@Valid @ModelAttribute ProductSearchRequest request) {
        return productSearchService.search(request);
    }

    /** Available categories and brands with product counts. */
    @GetMapping("/facets")
    public FacetResponse facets() {
        return productSearchService.getFacets();
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable String id) {
        return productSearchService.getById(id);
    }
}
