package com.example.rag.retrieval;

import com.example.rag.config.RagProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cross-encoder reranker served by Hugging Face Text Embeddings Inference (container
 * "reranker", model BAAI/bge-reranker-base).  POST /rerank  ->  [{"index":0,"score":0.97}, ...]
 */
public class CrossEncoderReranker implements Reranker {

    private record RerankRequest(String query, List<String> texts, boolean truncate) {
    }

    private record RerankScore(int index, double score) {
    }

    private final RestClient restClient;

    public CrossEncoderReranker(RagProperties.Reranker settings) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(settings.timeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(settings.timeout());
        this.restClient = RestClient.builder().baseUrl(settings.baseUrl()).requestFactory(factory).build();
    }

    @Override
    public List<Double> score(String query, List<String> texts) {
        RerankScore[] response = restClient.post()
                .uri("/rerank")
                .contentType(MediaType.APPLICATION_JSON)
                // truncate=true: texts longer than the model's input size are cut, not rejected
                .body(new RerankRequest(query, texts, true))
                .retrieve()
                .body(RerankScore[].class);

        List<Double> scores = new ArrayList<>(Collections.nCopies(texts.size(), 0.0));
        if (response != null) {
            for (RerankScore s : response) {
                if (s.index() >= 0 && s.index() < scores.size()) {
                    scores.set(s.index(), s.score());
                }
            }
        }
        return scores;
    }

    @Override
    public String name() {
        return "cross-encoder";
    }
}
