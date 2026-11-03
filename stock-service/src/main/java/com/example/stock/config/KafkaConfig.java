package com.example.stock.config;

import com.example.stock.messaging.InvalidEventException;
import com.example.stock.outbox.OutboxProperties;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka WIRING only - every VALUE comes from application.yml:
 *   spring.kafka.*               consumer, producer, listener (Spring Boot auto-configuration)
 *   spring.kafka.properties.*    Schema Registry and Avro settings (shared by both sides)
 *   app.kafka.error-handling.*   retry timing and DLT naming
 *   app.outbox.*                 outbox relay
 *
 * @EnableScheduling runs the OutboxRelay.
 */
@Configuration
@EnableKafka
@EnableScheduling
@EnableConfigurationProperties({KafkaErrorHandlingProperties.class, OutboxProperties.class})
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    /** Serializes outbox events. Configured from the producer's YAML settings. */
    @Bean
    public KafkaAvroSerializer outboxAvroSerializer(ProducerFactory<?, ?> producerFactory) {
        KafkaAvroSerializer serializer = new KafkaAvroSerializer();
        serializer.configure(producerFactory.getConfigurationProperties(), false);
        return serializer;
    }

    /** Decodes consumed events into the generated classes. Configured from the consumer's YAML settings. */
    @Bean
    public KafkaAvroDeserializer eventAvroDeserializer(ConsumerFactory<?, ?> consumerFactory) {
        KafkaAvroDeserializer deserializer = new KafkaAvroDeserializer();
        deserializer.configure(consumerFactory.getConfigurationProperties(), false);
        return deserializer;
    }

    /**
     * Applied by Spring Boot to every @KafkaListener.
     *  - TRANSIENT errors (database, Schema Registry, optimistic-lock conflict): retried
     *    with exponential backoff and no limit - the partition waits, order is kept.
     *  - PERMANENT errors (listed below): straight to <topic><dlt-suffix>.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> kafkaTemplate,
                                                 KafkaErrorHandlingProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    log.error("Sending record to DLT: topic={} partition={} offset={} error={}",
                            record.topic(), record.partition(), record.offset(), ex.getMessage());
                    return new TopicPartition(record.topic() + properties.dltSuffix(), record.partition());
                });

        ExponentialBackOff backOff = new ExponentialBackOff(
                properties.initialInterval().toMillis(), properties.multiplier());
        backOff.setMaxInterval(properties.maxInterval().toMillis());

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(
                InvalidEventException.class, ClassCastException.class, IllegalArgumentException.class);
        return errorHandler;
    }
}
