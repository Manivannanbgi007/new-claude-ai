package com.example.product.mapper;

import com.example.product.model.Product;
import com.example.product.projection.InvalidChangeEventException;
import com.example.product.projection.ProductRow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * WRITE MODEL row -> READ MODEL document.
 * This class is the ONLY place that knows both shapes. When the MySQL table changes,
 * this is the one class to adapt; the search API does not notice.
 */
@Component
public class ProductDocumentMapper {

    public Product toDocument(ProductRow row) {
        if (row.id() == null || row.sku() == null || row.version() == null) {
            throw new InvalidChangeEventException("Change event row is missing id, sku or version: " + row);
        }
        Product doc = new Product();
        doc.setId(row.id());
        doc.setSku(row.sku());
        doc.setName(row.name());
        doc.setDescription(row.description());
        doc.setBrand(row.brand());
        doc.setCategory(row.category());
        doc.setPrice(row.price() == null ? null : new BigDecimal(row.price()).doubleValue());
        doc.setRamGb(row.ramGb());
        doc.setRating(row.rating());
        doc.setTags(splitTags(row.tags()));
        doc.setVersion(row.version());
        doc.setCreatedAt(row.createdAt() == null ? null : Instant.ofEpochMilli(row.createdAt()));
        doc.setUpdatedAt(row.updatedAt() == null ? null : Instant.ofEpochMilli(row.updatedAt()));
        return doc;
    }

    /** "gaming,rtx" -> ["gaming", "rtx"] (an array is what the keyword field and filters expect). */
    private static List<String> splitTags(String tags) {
        return tags == null || tags.isBlank() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(tags.split(",")));
    }
}
