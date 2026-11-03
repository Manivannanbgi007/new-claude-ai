package com.example.productwrite.controller;

import com.example.productwrite.dto.ProductCreateRequest;
import com.example.productwrite.dto.ProductResponse;
import com.example.productwrite.dto.ProductUpdateRequest;
import com.example.productwrite.service.ProductCommandService;
import com.example.productwrite.util.AppConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Product commands. Same URL as the read side (/api/v1/products): the gateway
 * sends POST/PUT/DELETE here and GET to product-service.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductCommandController {

    private final ProductCommandService commandService;

    public ProductCommandController(ProductCommandService commandService) {
        this.commandService = commandService;
    }

    /**
     * 201 Created. The Location header points to GET /api/v1/products/{id}, which the
     * gateway serves from the READ side. The search index is updated asynchronously,
     * normally within about a second (eventual consistency).
     */
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductCreateRequest request) {
        ProductResponse created = commandService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable @Pattern(regexp = AppConstants.ID_REGEX) String id,
                                  @Valid @RequestBody ProductUpdateRequest request) {
        return commandService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Pattern(regexp = AppConstants.ID_REGEX) String id) {
        commandService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Source-of-truth read. Only reachable directly on port 8089, NOT through the gateway. */
    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable @Pattern(regexp = AppConstants.ID_REGEX) String id) {
        return commandService.getById(id);
    }
}
