package com.example.mcpserver.config;

import com.example.mcpserver.client.CorrelationIdInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class RestClientConfig {

    /**
     * HTTP client for the API Gateway - used by ALL gateway clients (products, stock, orders).
     * Timeouts: without them a hanging backend would block the tool call forever.
     *
     * Bean name "gatewayHttpClient" - it must differ from every @Component class name
     * (e.g. ProductApiRestClient -> bean "productApiRestClient").
     */
    @Bean
    public RestClient gatewayHttpClient(ProductApiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor(new CorrelationIdInterceptor())
                .build();
    }
}
