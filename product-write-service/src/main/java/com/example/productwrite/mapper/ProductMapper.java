package com.example.productwrite.mapper;

import com.example.productwrite.dto.ProductCreateRequest;
import com.example.productwrite.dto.ProductResponse;
import com.example.productwrite.dto.ProductUpdateRequest;
import com.example.productwrite.model.Product;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class ProductMapper {

    public Product toEntity(ProductCreateRequest request) {
        Product product = new Product(UUID.randomUUID().toString(), request.sku().trim().toUpperCase(Locale.ROOT));
        product.setName(request.name().trim());
        product.setDescription(trimToNull(request.description()));
        product.setBrand(request.brand().trim());
        product.setCategory(request.category().trim().toLowerCase(Locale.ROOT));
        product.setPrice(request.price());
        product.setRamGb(request.ramGb());
        product.setRating(request.rating());
        product.setTags(distinct(request.tags()));
        return product;
    }

    public void applyUpdate(Product product, ProductUpdateRequest request) {
        product.setName(request.name().trim());
        product.setDescription(trimToNull(request.description()));
        product.setBrand(request.brand().trim());
        product.setCategory(request.category().trim().toLowerCase(Locale.ROOT));
        product.setPrice(request.price());
        product.setRamGb(request.ramGb());
        product.setRating(request.rating());
        product.setTags(distinct(request.tags()));
    }

    public ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getBrand(),
                p.getCategory(), p.getPrice(), p.getRamGb(), p.getRating(), List.copyOf(p.getTags()),
                p.getVersion(), toInstant(p.getCreatedAt()), toInstant(p.getUpdatedAt()));
    }

    private static List<String> distinct(List<String> tags) {
        return tags == null ? new ArrayList<>() : new ArrayList<>(tags.stream().distinct().toList());
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static java.time.Instant toInstant(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
    }
}
