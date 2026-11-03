package com.example.mcpserver.client;

import com.example.mcpserver.dto.api.ApiPage;
import com.example.mcpserver.dto.api.ApiRagDocument;
import com.example.mcpserver.dto.api.ApiRagSearchRequest;
import com.example.mcpserver.dto.api.ApiRagSearchResponse;

/** Talks to rag-service through the API Gateway. */
public interface RagApiClient {

    ApiRagSearchResponse search(ApiRagSearchRequest request);

    ApiPage<ApiRagDocument> listReadyDocuments(int size);
}
