package com.example.product.repository;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.example.product.model.Product;
import com.example.product.model.ProductSearchCriteria;
import com.example.product.model.ProductSortField;
import com.example.product.model.SortDirection;
import com.example.product.util.ProductFields;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Search queries that are too flexible for derived repository methods.
 * Builds native Elasticsearch queries with the official Java client DSL.
 */
@Repository
public class ProductSearchRepository {

    private static final String TERMS_AGGREGATION = "terms_by_field";

    private final ElasticsearchOperations operations;

    public ProductSearchRepository(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    /** Full-text search + filters + sorting + pagination in one Elasticsearch request. */
    public SearchHits<Product> search(ProductSearchCriteria criteria) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(buildQuery(criteria))
                .withSort(buildSort(criteria))
                .withPageable(PageRequest.of(criteria.page(), criteria.size()))
                // Without this, Elasticsearch stops counting at 10,000 hits.
                .withTrackTotalHits(true)
                .build();
        return operations.search(query, Product.class);
    }

    /**
     * Counts products per distinct value of a keyword field (a "terms aggregation").
     * Returns e.g. {"laptop"=13, "smartphone"=6}, most frequent first.
     */
    public Map<String, Long> countByTerm(String field, int maxTerms) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(Query.of(q -> q.matchAll(m -> m)))
                .withAggregation(TERMS_AGGREGATION,
                        Aggregation.of(a -> a.terms(t -> t.field(field).size(maxTerms))))
                // We only need the counts, not the documents themselves.
                .withMaxResults(0)
                .build();

        SearchHits<Product> hits = operations.search(query, Product.class);

        Map<String, Long> counts = new LinkedHashMap<>();
        if (hits.getAggregations() instanceof ElasticsearchAggregations aggregations) {
            ElasticsearchAggregation aggregation = aggregations.aggregationsAsMap().get(TERMS_AGGREGATION);
            if (aggregation != null) {
                aggregation.aggregation().getAggregate().sterms().buckets().array()
                        .forEach(bucket -> counts.put(bucket.key().stringValue(), bucket.docCount()));
            }
        }
        return counts;
    }

    /**
     * must   = affects the relevance score (the text search).
     * filter = yes/no conditions, no scoring, cached by Elasticsearch (fast).
     * A bool query without any clause matches all documents.
     */
    private Query buildQuery(ProductSearchCriteria c) {
        BoolQuery.Builder bool = new BoolQuery.Builder();

        if (StringUtils.hasText(c.text())) {
            bool.must(m -> m.multiMatch(mm -> mm
                    .query(c.text())
                    // ^3 = a match in name counts 3x more than in description
                    .fields(ProductFields.NAME + "^3", ProductFields.BRAND + "^2",
                            ProductFields.TAGS + "^2", ProductFields.DESCRIPTION)
                    // Tolerates small typos: "gamng" still finds "gaming"
                    .fuzziness("AUTO")));
        }

        if (StringUtils.hasText(c.brand())) {
            bool.filter(f -> f.term(t -> t.field(ProductFields.BRAND).value(c.brand())));
        }
        if (StringUtils.hasText(c.category())) {
            bool.filter(f -> f.term(t -> t.field(ProductFields.CATEGORY).value(c.category())));
        }
        if (StringUtils.hasText(c.tag())) {
            bool.filter(f -> f.term(t -> t.field(ProductFields.TAGS).value(c.tag())));
        }

        addRangeFilter(bool, ProductFields.PRICE, c.minPrice(), c.maxPrice());
        addRangeFilter(bool, ProductFields.RAM_GB, toDouble(c.minRamGb()), toDouble(c.maxRamGb()));
        addRangeFilter(bool, ProductFields.RATING, c.minRating(), null);

        return bool.build()._toQuery();
    }

    /** Adds "field >= min AND field <= max"; either bound may be null (open range). */
    private void addRangeFilter(BoolQuery.Builder bool, String field, Double min, Double max) {
        if (min == null && max == null) {
            return;
        }
        bool.filter(f -> f.range(r -> r.number(n -> {
            n.field(field);
            if (min != null) {
                n.gte(min);
            }
            if (max != null) {
                n.lte(max);
            }
            return n;
        })));
    }

    private List<SortOptions> buildSort(ProductSearchCriteria c) {
        List<SortOptions> sorts = new ArrayList<>();
        SortOrder order = c.sortDirection() == SortDirection.ASC ? SortOrder.Asc : SortOrder.Desc;

        if (c.sortBy() == ProductSortField.RELEVANCE) {
            sorts.add(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));
        } else {
            sorts.add(SortOptions.of(s -> s.field(f -> f.field(c.sortBy().getField()).order(order))));
        }
        // Tie-breaker: products with the same price keep a stable order across pages.
        sorts.add(SortOptions.of(s -> s.field(f -> f.field(ProductFields.SKU).order(SortOrder.Asc))));
        return sorts;
    }

    private static Double toDouble(Integer value) {
        return value == null ? null : value.doubleValue();
    }
}
