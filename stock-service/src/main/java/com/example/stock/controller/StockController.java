package com.example.stock.controller;

import com.example.stock.dto.AvailabilityCheckRequest;
import com.example.stock.dto.AvailabilityCheckResponse;
import com.example.stock.dto.AvailabilityResponse;
import com.example.stock.dto.PageResponse;
import com.example.stock.dto.StockAdjustmentRequest;
import com.example.stock.dto.StockItemRequest;
import com.example.stock.dto.StockItemResponse;
import com.example.stock.dto.StockItemUpdateRequest;
import com.example.stock.service.AvailabilityService;
import com.example.stock.service.StockService;
import com.example.stock.util.AppConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Stock records and availability checks.
 * Constraints on @PathVariable/@RequestParam are checked by Spring MVC's built-in
 * method validation; violations return 400 through GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/v1/stock")
public class StockController {

    private final StockService stockService;
    private final AvailabilityService availabilityService;

    public StockController(StockService stockService, AvailabilityService availabilityService) {
        this.stockService = stockService;
        this.availabilityService = availabilityService;
    }

    // ---------------- availability ----------------

    /** "Can I buy 2 of these?" - used when a product is selected. */
    @GetMapping("/{sku}/availability")
    public AvailabilityResponse availability(
            @PathVariable @Pattern(regexp = AppConstants.SKU_REGEX) String sku,
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "quantity must be at least 1")
            @Max(value = AppConstants.MAX_QUANTITY_PER_LINE, message = "quantity must be at most 1000")
            int quantity) {
        return availabilityService.check(sku, quantity);
    }

    /** Check a whole cart in one call. Unknown SKUs are reported as NOT_STOCKED. */
    @PostMapping("/availability")
    public AvailabilityCheckResponse checkAvailability(@Valid @RequestBody AvailabilityCheckRequest request) {
        return availabilityService.checkAll(request);
    }

    // ---------------- stock records ----------------

    @PostMapping
    public ResponseEntity<StockItemResponse> create(@Valid @RequestBody StockItemRequest request) {
        StockItemResponse created = stockService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{sku}")
                .buildAndExpand(created.sku())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** All stock records, sorted by SKU. lowStockOnly=true shows what needs restocking. */
    @GetMapping
    public PageResponse<StockItemResponse> list(
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be 0 or greater") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must be at most 100")
            int size) {
        return stockService.list(lowStockOnly, page, size);
    }

    @GetMapping("/{sku}")
    public StockItemResponse get(@PathVariable @Pattern(regexp = AppConstants.SKU_REGEX) String sku) {
        return stockService.getBySku(sku);
    }

    /** Replace counted quantity and reorder level (after a stock count). */
    @PutMapping("/{sku}")
    public StockItemResponse update(@PathVariable @Pattern(regexp = AppConstants.SKU_REGEX) String sku,
                                    @Valid @RequestBody StockItemUpdateRequest request) {
        return stockService.update(sku, request);
    }

    /** +/- change: deliveries received, damaged goods, returns. */
    @PostMapping("/{sku}/adjustments")
    public StockItemResponse adjust(@PathVariable @Pattern(regexp = AppConstants.SKU_REGEX) String sku,
                                    @Valid @RequestBody StockAdjustmentRequest request) {
        return stockService.adjust(sku, request);
    }

    @DeleteMapping("/{sku}")
    public ResponseEntity<Void> delete(@PathVariable @Pattern(regexp = AppConstants.SKU_REGEX) String sku) {
        stockService.delete(sku);
        return ResponseEntity.noContent().build();
    }
}
