package com.example.product.service.impl;

import com.example.product.dto.FacetBucket;
import com.example.product.dto.FacetResponse;
import com.example.product.dto.PageResponse;
import com.example.product.dto.ProductResponse;
import com.example.product.dto.ProductSearchRequest;
import com.example.product.exception.InvalidSearchRequestException;
import com.example.product.exception.ProductNotFoundException;
import com.example.product.mapper.ProductMapper;
import com.example.product.model.Product;
import com.example.product.model.ProductSearchCriteria;
import com.example.product.repository.ProductRepository;
import com.example.product.repository.ProductSearchRepository;
import com.example.product.service.ProductSearchService;
import com.example.product.util.AppConstants;
import com.example.product.util.ProductFields;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ProductSearchServiceImpl implements ProductSearchService {

    private static final int MAX_FACET_VALUES = 100;

    private final ProductSearchRepository searchRepository;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductSearchServiceImpl(ProductSearchRepository searchRepository, ProductRepository productRepository,
                                    ProductMapper productMapper) {
        this.searchRepository = searchRepository;
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    @Override
    public ProductResponse getById(String id) {
        return productRepository.findById(id)
                .map(productMapper::toResponse)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    public PageResponse<ProductResponse> search(ProductSearchRequest request) {
        validate(request);

        ProductSearchCriteria criteria = productMapper.toCriteria(request);
        SearchHits<Product> hits = searchRepository.search(criteria);

        List<ProductResponse> content = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(productMapper::toResponse)
                .toList();

        return PageResponse.of(content, criteria.page(), criteria.size(), hits.getTotalHits());
    }

    @Override
    public FacetResponse getFacets() {
        return new FacetResponse(
                toBuckets(searchRepository.countByTerm(ProductFields.CATEGORY, MAX_FACET_VALUES)),
                toBuckets(searchRepository.countByTerm(ProductFields.BRAND, MAX_FACET_VALUES)));
    }

    /** Checks rules that involve more than one parameter. */
    private void validate(ProductSearchRequest request) {
        if (request.minPrice() != null && request.maxPrice() != null
                && request.minPrice().compareTo(request.maxPrice()) > 0) {
            throw new InvalidSearchRequestException("minPrice must be less than or equal to maxPrice");
        }
        if (request.minRamGb() != null && request.maxRamGb() != null
                && request.minRamGb() > request.maxRamGb()) {
            throw new InvalidSearchRequestException("minRamGb must be less than or equal to maxRamGb");
        }
        long lastRequestedResult = (long) (request.page() + 1) * request.size();
        if (lastRequestedResult > AppConstants.MAX_RESULT_WINDOW) {
            throw new InvalidSearchRequestException("Only the first " + AppConstants.MAX_RESULT_WINDOW
                    + " results can be paged through. Narrow the search with filters.");
        }
    }

    private static List<FacetBucket> toBuckets(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> new FacetBucket(entry.getKey(), entry.getValue()))
                .toList();
    }
}
