package com.example.mcpserver.client;

import com.example.mcpserver.dto.api.ApiPage;
import com.example.mcpserver.dto.api.ApiRagDocument;
import com.example.mcpserver.dto.api.ApiRagSearchRequest;
import com.example.mcpserver.dto.api.ApiRagSearchResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RagApiRestClient implements RagApiClient {

    private static final ParameterizedTypeReference<ApiPage<ApiRagDocument>> DOCUMENT_PAGE = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public RagApiRestClient(@Qualifier("gatewayHttpClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ApiRagSearchResponse search(ApiRagSearchRequest request) {
        return GatewayCalls.execute("knowledge base", () -> restClient.post()
                .uri("/api/v1/rag/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ApiRagSearchResponse.class));
    }

    @Override
    public ApiPage<ApiRagDocument> listReadyDocuments(int size) {
        return GatewayCalls.execute("knowledge base", () -> restClient.get()
                .uri("/api/v1/rag/documents?status=ready&size={size}", size)
                .retrieve()
                .body(DOCUMENT_PAGE));
    }
}
