package com.example.product.config;

import com.example.product.projection.InvalidChangeEventException;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka WIRING only. Every VALUE (servers, group id, serializers, Schema Registry URL,
 * retry timing, DLT name) comes from application.yml:
 *   spring.kafka.*               -> Spring Boot creates the consumer, producer, listener factory
 *   app.kafka.error-handling.*   -> retry and dead-letter behaviour below
 *
 * @EnableKafka is a safety net: if Kafka auto-configuration were ever missing, the app
 * fails at startup with a clear error instead of silently not starting the listener.
 */
@Configuration
@EnableKafka
@EnableConfigurationProperties(KafkaErrorHandlingProperties.class)
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    /**
     * Confluent Avro deserializer, configured from the SAME settings as the consumer
     * (spring.kafka.properties + spring.kafka.consumer), so schema.registry.url and
     * specific.avro.reader are only defined once, in YAML.
     */
    @Bean
    public KafkaAvroDeserializer cdcAvroDeserializer(ConsumerFactory<?, ?> consumerFactory) {
        KafkaAvroDeserializer deserializer = new KafkaAvroDeserializer();
        deserializer.configure(consumerFactory.getConfigurationProperties(), false);
        return deserializer;
    }

    /**
     * Spring Boot automatically applies this error handler to every @KafkaListener.
     *  - TRANSIENT errors: retried with exponential backoff, without a limit, so no
     *    change is skipped or applied out of order;
     *  - PERMANENT errors (listed below): sent straight to the dead letter topic.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> kafkaTemplate,
                                                 KafkaErrorHandlingProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    log.error("Sending record to DLT: topic={} partition={} offset={} error={}",
                            record.topic(), record.partition(), record.offset(), ex.getMessage());
                    // Same partition number: the DLT has the same partition count as the source topic.
                    return new TopicPartition(record.topic() + properties.dltSuffix(), record.partition());
                });

        ExponentialBackOff backOff = new ExponentialBackOff(
                properties.initialInterval().toMillis(), properties.multiplier());
        backOff.setMaxInterval(properties.maxInterval().toMillis());

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // Errors that can NEVER succeed on retry (this is code, not configuration:
        // it describes what the exceptions mean).
        errorHandler.addNotRetryableExceptions(
                InvalidChangeEventException.class, ClassCastException.class, IllegalArgumentException.class);
        return errorHandler;
    }
}
