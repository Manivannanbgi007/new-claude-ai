package com.example.productwrite.config;

import com.example.productwrite.outbox.OutboxProperties;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Kafka WIRING only (this service only PUBLISHES). All values are in application.yml
 * (spring.kafka.* and app.outbox.*). @EnableScheduling runs the OutboxRelay.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class KafkaConfig {

    /** Serializes outbox events; configured from the producer's YAML settings. */
    @Bean
    public KafkaAvroSerializer outboxAvroSerializer(ProducerFactory<?, ?> producerFactory) {
        KafkaAvroSerializer serializer = new KafkaAvroSerializer();
        serializer.configure(producerFactory.getConfigurationProperties(), false);
        return serializer;
    }
}
