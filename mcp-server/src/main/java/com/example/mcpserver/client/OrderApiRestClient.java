package com.example.mcpserver.client;

import com.example.mcpserver.dto.api.ApiCreateOrderRequest;
import com.example.mcpserver.dto.api.ApiOrder;
import com.example.mcpserver.dto.api.ApiPage;
import com.example.mcpserver.exception.BackendApiException;
import com.example.mcpserver.exception.OrderNotFoundException;
import com.example.mcpserver.util.AppConstants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/** Every order call carries X-User-Id, so the gateway and order-service can trace the user. */
@Component
public class OrderApiRestClient implements OrderApiClient {

    private static final String ORDERS_PATH = "/api/v1/orders";
    private static final ParameterizedTypeReference<ApiPage<ApiOrder>> ORDER_PAGE = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public OrderApiRestClient(@Qualifier("gatewayHttpClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ApiOrder create(ApiCreateOrderRequest request, String idempotencyKey) {
        return GatewayCalls.execute("order service", () -> restClient.post()
                .uri(ORDERS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(AppConstants.USER_ID_HEADER, request.userId())
                .header(AppConstants.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .body(request)
                .retrieve()
                .body(ApiOrder.class));
    }

    @Override
    public ApiOrder getById(String orderId) {
        try {
            return GatewayCalls.execute("order service", () -> restClient.get()
                    .uri(ORDERS_PATH + "/{id}", orderId)
                    .retrieve()
                    .body(ApiOrder.class));
        } catch (BackendApiException ex) {
            if (ex.isNotFound()) {
                throw new OrderNotFoundException(orderId);
            }
            throw ex;
        }
    }

    @Override
    public ApiPage<ApiOrder> listForUser(String userId, String status, int page, int size) {
        return GatewayCalls.execute("order service", () -> restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(ORDERS_PATH);
                    Map<String, Object> variables = new LinkedHashMap<>();
                    variables.put("userId", userId);
                    variables.put("page", page);
                    variables.put("size", size);
                    uriBuilder.queryParam("userId", "{userId}").queryParam("page", "{page}").queryParam("size", "{size}");
                    if (status != null) {
                        uriBuilder.queryParam("status", "{status}");
                        variables.put("status", status);
                    }
                    return uriBuilder.build(variables);
                })
                .header(AppConstants.USER_ID_HEADER, userId)
                .retrieve()
                .body(ORDER_PAGE));
    }
}
