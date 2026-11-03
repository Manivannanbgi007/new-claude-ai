package com.example.stock.messaging;

import com.example.events.EventTopics;
import com.example.stock.outbox.OutboxWriter;
import org.apache.avro.specific.SpecificRecordBase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the OUTCOME of handling an order event, in ONE transaction:
 *   - the reply event (if any) in the outbox
 *   - the "processed" marker for the incoming event
 * Both are committed together, so an event is never marked processed without its reply.
 */
@Component
public class SagaEventRecorder {

    private final ProcessedEventRepository processedEventRepository;
    private final OutboxWriter outboxWriter;

    public SagaEventRecorder(ProcessedEventRepository processedEventRepository, OutboxWriter outboxWriter) {
        this.processedEventRepository = processedEventRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public void record(String incomingEventId, String incomingEventType, String orderId, SpecificRecordBase reply) {
        if (reply != null) {
            outboxWriter.write(EventTopics.STOCK_EVENTS, orderId, reply);
        }
        processedEventRepository.save(new ProcessedEvent(incomingEventId, incomingEventType));
    }
}
