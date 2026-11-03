package com.example.order.service.impl;

import com.example.events.product.ProductSnapshot;
import com.example.order.dto.CatalogProductResponse;
import com.example.order.dto.PageResponse;
import com.example.order.exception.UnknownProductException;
import com.example.order.mapper.OrderMapper;
import com.example.order.model.CatalogProduct;
import com.example.order.repository.CatalogProductRepository;
import com.example.order.service.ProductCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

/**
 * Applies ProductSnapshot events (upsert) and tombstones (delete).
 * Idempotent by nature: applying the same snapshot twice gives the same row.
 * An OLDER snapshot than the stored one is ignored (e.g. after a replay).
 */
@Service
public class ProductCatalogServiceImpl implements ProductCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogServiceImpl.class);

    private final CatalogProductRepository repository;
    private final OrderMapper mapper;

    public ProductCatalogServiceImpl(CatalogProductRepository repository, OrderMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void apply(ProductSnapshot snapshot) {
        String sku = snapshot.getSku().toUpperCase(Locale.ROOT);
        LocalDateTime occurredAt = LocalDateTime.ofInstant(snapshot.getOccurredAt(), ZoneOffset.UTC);

        CatalogProduct product = repository.findById(sku).orElseGet(() -> new CatalogProduct(sku));
        if (product.getSourceOccurredAt() != null && product.getSourceOccurredAt().isAfter(occurredAt)) {
            log.info("Older snapshot for {} ignored", sku);
            return;
        }
        product.apply(snapshot.getProductId(), snapshot.getName(), snapshot.getBrand(), snapshot.getCategory(),
                snapshot.getPrice().setScale(2, RoundingMode.HALF_UP), snapshot.getCurrency(), occurredAt);
        repository.save(product);
        log.debug("Catalogue updated: {} {} {}", sku, product.getPrice(), product.getCurrency());
    }

    @Override
    @Transactional
    public void remove(String sku) {
        String normalized = sku.toUpperCase(Locale.ROOT);
        if (repository.existsById(normalized)) {
            repository.deleteById(normalized);
            log.info("Catalogue: product {} removed", normalized);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CatalogProductResponse> list(int page, int size) {
        return PageResponse.from(repository.findAll(PageRequest.of(page, size, Sort.by("sku"))), mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogProductResponse get(String sku) {
        String normalized = sku.toUpperCase(Locale.ROOT);
        return repository.findById(normalized).map(mapper::toResponse)
                .orElseThrow(() -> new UnknownProductException(List.of(normalized)));
    }
}
