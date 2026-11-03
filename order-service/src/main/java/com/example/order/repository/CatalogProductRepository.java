package com.example.order.repository;

import com.example.order.model.CatalogProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogProductRepository extends JpaRepository<CatalogProduct, String> {
}
