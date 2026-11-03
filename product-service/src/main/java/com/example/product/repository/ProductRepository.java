package com.example.product.repository;

import com.example.product.model.Product;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Access to the "products" index. Only ProductProjector writes through it;
 * everything else in this service only reads.
 */
public interface ProductRepository extends ElasticsearchRepository<Product, String> {
}
