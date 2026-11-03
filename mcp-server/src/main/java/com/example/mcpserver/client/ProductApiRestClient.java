package com.example.mcpserver.client;

import com.example.mcpserver.dto.api.ApiFacets;
import com.example.mcpserver.dto.api.ApiPage;
import com.example.mcpserver.dto.api.ApiProduct;
import com.example.mcpserver.exception.BackendApiException;
import com.example.mcpserver.exception.ProductNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** RestClient implementation of ProductApiClient, calling the API Gateway. */
@Component
public class ProductApiRestClient implements ProductApiClient {

    private static final String PRODUCTS_PATH = "/api/v1/products";
    private static final ParameterizedTypeReference<ApiPage<ApiProduct>> PRODUCT_PAGE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public ProductApiRestClient(@Qualifier("gatewayHttpClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ApiPage<ApiProduct> search(Map<String, Object> queryParams) {
        return execute(() -> restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(PRODUCTS_PATH);
                    // Values are passed as URI variables ({name}) so special characters
                    // such as '&' or '#' in the search text are always encoded safely.
                    Map<String, Object> variables = new LinkedHashMap<>();
                    queryParams.forEach((name, value) -> {
                        if (value != null) {
                            uriBuilder.queryParam(name, "{" + name + "}");
                            variables.put(name, value);
                        }
                    });
                    return uriBuilder.build(variables);
                })
                .retrieve()
                .body(PRODUCT_PAGE), null);
    }

    @Override
    public ApiProduct getById(String id) {
        return execute(() -> restClient.get()
                .uri(PRODUCTS_PATH + "/{id}", id)
                .retrieve()
                .body(ApiProduct.class), id);
    }

    @Override
    public ApiFacets getFacets() {
        return execute(() -> restClient.get()
                .uri(PRODUCTS_PATH + "/facets")
                .retrieve()
                .body(ApiFacets.class), null);
    }

    /** Shared error handling; a 404 for a product id becomes ProductNotFoundException. */
    private <T> T execute(Supplier<T> call, String productId) {
        try {
            return GatewayCalls.execute("product catalogue", call);
        } catch (BackendApiException ex) {
            if (ex.isNotFound() && productId != null) {
                throw new ProductNotFoundException(productId);
            }
            throw ex;
        }
    }
}
