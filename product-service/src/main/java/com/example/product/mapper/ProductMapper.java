package com.example.product.mapper;

import com.example.product.dto.ProductResponse;
import com.example.product.dto.ProductSearchRequest;
import com.example.product.model.Product;
import com.example.product.model.ProductSearchCriteria;
import com.example.product.model.ProductSortField;
import com.example.product.model.SortDirection;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Converts read-model documents into API responses and search requests into criteria.
 * (Writes are handled by product-write-service; see projection/ for how documents are built.)
 */
@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getBrand(),
                product.getCategory(),
                toMoney(product.getPrice()),
                product.getRamGb(),
                product.getRating(),
                product.getTags() == null ? List.of() : List.copyOf(product.getTags()),
                product.getVersion(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    /**
     * Resolves defaults: with a search text the best matches come first,
     * without one the newest products come first.
     */
    public ProductSearchCriteria toCriteria(ProductSearchRequest request) {
        String text = trimToNull(request.q());

        ProductSortField sortBy = request.sortBy();
        if (sortBy == null) {
            sortBy = text != null ? ProductSortField.RELEVANCE : ProductSortField.NEWEST;
        }
        SortDirection direction = request.sortDirection() != null
                ? request.sortDirection()
                : sortBy.getDefaultDirection();

        return new ProductSearchCriteria(
                text,
                trimToNull(request.brand()),
                trimToNull(request.category()),
                toDouble(request.minPrice()),
                toDouble(request.maxPrice()),
                request.minRamGb(),
                request.maxRamGb(),
                request.minRating(),
                trimToNull(request.tag()),
                sortBy,
                direction,
                request.page(),
                request.size());
    }

    private static BigDecimal toMoney(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
