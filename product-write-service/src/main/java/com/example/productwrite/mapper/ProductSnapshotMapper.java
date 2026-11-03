package com.example.productwrite.mapper;

import com.example.events.product.ProductSnapshot;
import com.example.productwrite.model.Product;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** Product entity -> ProductSnapshot Avro event (the PUBLIC catalogue contract). */
@Component
public class ProductSnapshotMapper {

    public ProductSnapshot toSnapshot(Product product) {
        return ProductSnapshot.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setProductId(product.getId())
                .setSku(product.getSku())
                .setName(product.getName())
                .setBrand(product.getBrand())
                .setCategory(product.getCategory())
                // Avro decimal(12,2) requires EXACTLY scale 2
                .setPrice(product.getPrice().setScale(2, RoundingMode.HALF_UP))
                .setCurrency("USD")
                .build();
    }
}
