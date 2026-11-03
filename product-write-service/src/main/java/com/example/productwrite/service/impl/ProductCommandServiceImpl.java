package com.example.productwrite.service.impl;

import com.example.events.EventTopics;
import com.example.productwrite.dto.ProductCreateRequest;
import com.example.productwrite.dto.ProductResponse;
import com.example.productwrite.dto.ProductUpdateRequest;
import com.example.productwrite.exception.DuplicateSkuException;
import com.example.productwrite.exception.ProductNotFoundException;
import com.example.productwrite.exception.StaleVersionException;
import com.example.productwrite.mapper.ProductMapper;
import com.example.productwrite.mapper.ProductSnapshotMapper;
import com.example.productwrite.model.Product;
import com.example.productwrite.outbox.OutboxWriter;
import com.example.productwrite.repository.ProductRepository;
import com.example.productwrite.service.ProductCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Each method is ONE MySQL transaction containing:
 *   1. the product change            -> Debezium CDC -> search index (read side)
 *   2. a ProductSnapshot in the outbox -> OutboxRelay  -> product.catalog.v1 (other services)
 * Both are committed together or not at all (no dual write).
 */
@Service
public class ProductCommandServiceImpl implements ProductCommandService {

    private static final Logger log = LoggerFactory.getLogger(ProductCommandServiceImpl.class);

    private final ProductRepository productRepository;
    private final ProductMapper mapper;
    private final ProductSnapshotMapper snapshotMapper;
    private final OutboxWriter outboxWriter;

    public ProductCommandServiceImpl(ProductRepository productRepository, ProductMapper mapper,
                                     ProductSnapshotMapper snapshotMapper, OutboxWriter outboxWriter) {
        this.productRepository = productRepository;
        this.mapper = mapper;
        this.snapshotMapper = snapshotMapper;
        this.outboxWriter = outboxWriter;
    }

    @Override
    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        Product product = mapper.toEntity(request);
        if (productRepository.existsBySku(product.getSku())) {
            throw new DuplicateSkuException(product.getSku());
        }
        Product saved = productRepository.saveAndFlush(product);
        outboxWriter.write(EventTopics.PRODUCT_CATALOG, saved.getSku(), snapshotMapper.toSnapshot(saved));
        log.info("Created product id={} sku={}", saved.getId(), saved.getSku());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponse update(String id, ProductUpdateRequest request) {
        Product product = findOrThrow(id);
        if (!product.getVersion().equals(request.version())) {
            throw new StaleVersionException(id, request.version(), product.getVersion());
        }
        mapper.applyUpdate(product, request);
        // saveAndFlush: Hibernate runs "UPDATE ... WHERE id=? AND version=?" now,
        // so a concurrent update of the same version fails here with 409.
        Product saved = productRepository.saveAndFlush(product);
        outboxWriter.write(EventTopics.PRODUCT_CATALOG, saved.getSku(), snapshotMapper.toSnapshot(saved));
        log.info("Updated product id={} version={}", saved.getId(), saved.getVersion());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(String id) {
        Product product = findOrThrow(id);
        productRepository.delete(product);
        // Tombstone: consumers of the compacted catalogue topic remove the product.
        outboxWriter.writeTombstone(EventTopics.PRODUCT_CATALOG, product.getSku());
        log.info("Deleted product id={} sku={}", product.getId(), product.getSku());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(String id) {
        return mapper.toResponse(findOrThrow(id));
    }

    private Product findOrThrow(String id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }
}
