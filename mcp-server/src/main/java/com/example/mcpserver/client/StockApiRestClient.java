package com.example.mcpserver.client;

import com.example.mcpserver.dto.api.ApiAvailability;
import com.example.mcpserver.exception.BackendApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class StockApiRestClient implements StockApiClient {

    private final RestClient restClient;

    public StockApiRestClient(@Qualifier("gatewayHttpClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<ApiAvailability> checkAvailability(String sku, int quantity) {
        try {
            return Optional.ofNullable(GatewayCalls.execute("stock service", () -> restClient.get()
                    .uri("/api/v1/stock/{sku}/availability?quantity={quantity}", sku, quantity)
                    .retrieve()
                    .body(ApiAvailability.class)));
        } catch (BackendApiException ex) {
            if (ex.isNotFound()) {
                return Optional.empty();   // no stock record = the product is not stocked
            }
            throw ex;
        }
    }
}
