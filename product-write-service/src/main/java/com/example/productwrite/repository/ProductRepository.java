package com.example.productwrite.repository;

import com.example.productwrite.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, String> {

    boolean existsBySku(String sku);
}
