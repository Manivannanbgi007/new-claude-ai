package com.example.productwrite.bootstrap;

import com.example.productwrite.dto.ProductCreateRequest;
import com.example.productwrite.repository.ProductRepository;
import com.example.productwrite.service.ProductCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Inserts the sample products through the normal command service - the same path
 * as a real POST - so they flow through Debezium into Elasticsearch like any change.
 * Runs only when app.sample-data.enabled=true AND the product table is empty.
 */
@Component
@ConditionalOnProperty(name = "app.sample-data.enabled", havingValue = "true")
public class SampleProductLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleProductLoader.class);

    private final ProductRepository productRepository;
    private final ProductCommandService commandService;

    public SampleProductLoader(ProductRepository productRepository, ProductCommandService commandService) {
        this.productRepository = productRepository;
        this.commandService = commandService;
    }

    @Override
    public void run(ApplicationArguments args) {
        long existing = productRepository.count();
        if (existing > 0) {
            log.info("Sample products skipped: product table already contains {} rows", existing);
            return;
        }
        List<ProductCreateRequest> products = SampleProductData.products();
        products.forEach(commandService::create);
        log.info("Sample products loaded into MySQL: {} products", products.size());
    }
}
