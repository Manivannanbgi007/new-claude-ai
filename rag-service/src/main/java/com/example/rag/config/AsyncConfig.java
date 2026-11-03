package com.example.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Document ingestion runs in the background: parsing and embedding a large PDF on CPU can
 * take minutes, and the upload request must not wait for it (202 Accepted + status polling).
 * A small, BOUNDED pool: when the queue is full, uploads are rejected instead of piling up.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "ingestionExecutor")
    public Executor ingestionExecutor(RagProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.ingestion().workerThreads());
        executor.setMaxPoolSize(properties.ingestion().workerThreads());
        executor.setQueueCapacity(properties.ingestion().queueCapacity());
        executor.setThreadNamePrefix("ingest-");
        executor.initialize();
        return executor;
    }
}
