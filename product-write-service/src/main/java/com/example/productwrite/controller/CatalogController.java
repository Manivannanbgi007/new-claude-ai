package com.example.productwrite.controller;

import com.example.productwrite.service.CatalogPublishService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Operations endpoint: publish the whole catalogue to product.catalog.v1 again. */
@RestController
@RequestMapping("/api/v1/products/catalog")
public class CatalogController {

    private final CatalogPublishService catalogPublishService;

    public CatalogController(CatalogPublishService catalogPublishService) {
        this.catalogPublishService = catalogPublishService;
    }

    @PostMapping("/republish")
    public Map<String, Integer> republish() {
        return Map.of("queued", catalogPublishService.republishAll());
    }
}
