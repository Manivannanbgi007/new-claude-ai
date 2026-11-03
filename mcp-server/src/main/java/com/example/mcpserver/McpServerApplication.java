package com.example.mcpserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the MCP Server.
 * @ConfigurationPropertiesScan registers ProductApiProperties (app.product-api.*).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}
