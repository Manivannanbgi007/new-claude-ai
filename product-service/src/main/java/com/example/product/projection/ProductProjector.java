package com.example.product.projection;

import com.example.product.mapper.ProductDocumentMapper;
import com.example.product.model.Product;
import com.example.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Applies product change events to the Elasticsearch read model.
 *
 * Idempotent by design: applying the same event twice gives the same result.
 *  - upsert: if the index already holds this row version or a newer one, the event is
 *    a duplicate or a replay and is skipped;
 *  - delete of an already deleted document is a no-op.
 *
 * The "read version, then write" check is safe WITHOUT locks: all events of one
 * product have the same Kafka key, so they sit in one partition and are processed
 * one after another by exactly one consumer thread.
 */
@Service
public class ProductProjector {

    private static final Logger log = LoggerFactory.getLogger(ProductProjector.class);

    private final ProductRepository productRepository;
    private final ProductDocumentMapper mapper;

    public ProductProjector(ProductRepository productRepository, ProductDocumentMapper mapper) {
        this.productRepository = productRepository;
        this.mapper = mapper;
    }

    public void apply(ProductChangeEvent event) {
        switch (event.op() == null ? "" : event.op()) {
            case "r", "c", "u" -> upsert(requireRow(event.after(), event));
            case "d" -> delete(requireRow(event.before(), event));
            default -> log.warn("Ignoring change event with unsupported op '{}'", event.op());
        }
    }

    private void upsert(ProductRow row) {
        Product document = mapper.toDocument(row);

        Long stored = productRepository.findById(row.id()).map(Product::getVersion).orElse(null);
        if (stored != null && stored >= row.version()) {
            log.info("Skipped outdated change id={} version={} (index already has version {})",
                    row.id(), row.version(), stored);
            return;
        }

        productRepository.save(document);
        log.info("Projected product id={} sku={} version={}", row.id(), row.sku(), row.version());
    }

    private void delete(ProductRow row) {
        productRepository.deleteById(row.id());
        log.info("Removed product id={} sku={} from the index", row.id(), row.sku());
    }

    private static ProductRow requireRow(ProductRow row, ProductChangeEvent event) {
        if (row == null) {
            throw new InvalidChangeEventException("Change event op=" + event.op() + " has no row data");
        }
        return row;
    }
}
