package com.example.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Title and description of this service's OpenAPI document (GET /v3/api-docs).
 * The endpoints themselves are generated from the existing controllers - nothing to annotate.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI serviceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Service API")
                        .version("v1")
                        .description("Orders: create (202, saga via Kafka), status, history, cancel. Use the Idempotency-Key header to make POST safe to retry."))
                // RELATIVE server URL: "Try it out" calls the host the document was loaded from.
                // Loaded through the gateway (localhost:8080) -> requests go through the gateway,
                // with its routing, rate limiting and circuit breakers.
                .servers(List.of(new Server().url("/").description("Current host")));
    }
}
