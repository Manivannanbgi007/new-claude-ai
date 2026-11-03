package com.example.productwrite.service.impl;

import com.example.events.EventTopics;
import com.example.productwrite.mapper.ProductSnapshotMapper;
import com.example.productwrite.model.Product;
import com.example.productwrite.outbox.OutboxWriter;
import com.example.productwrite.repository.ProductRepository;
import com.example.productwrite.service.CatalogPublishService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Full re-publish, for example when a new consumer (order-service) needs the whole
 * catalogue, or products existed before publishing was introduced. Safe to repeat:
 * consumers simply overwrite their copy with the same data.
 */
@Service
public class CatalogPublishServiceImpl implements CatalogPublishService {

    private static final Logger log = LoggerFactory.getLogger(CatalogPublishServiceImpl.class);

    private final ProductRepository productRepository;
    private final ProductSnapshotMapper snapshotMapper;
    private final OutboxWriter outboxWriter;

    public CatalogPublishServiceImpl(ProductRepository productRepository, ProductSnapshotMapper snapshotMapper,
                                     OutboxWriter outboxWriter) {
        this.productRepository = productRepository;
        this.snapshotMapper = snapshotMapper;
        this.outboxWriter = outboxWriter;
    }

    @Override
    @Transactional
    public int republishAll() {
        List<Product> products = productRepository.findAll();
        products.forEach(p -> outboxWriter.write(EventTopics.PRODUCT_CATALOG, p.getSku(), snapshotMapper.toSnapshot(p)));
        log.info("Queued {} product snapshots for republishing", products.size());
        return products.size();
    }
}
