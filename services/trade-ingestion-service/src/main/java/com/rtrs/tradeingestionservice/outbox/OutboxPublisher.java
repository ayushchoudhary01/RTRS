package com.rtrs.tradeingestionservice.outbox;

import com.rtrs.tradeingestionservice.kafka.TradeEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final TradeEventProducer tradeEventProducer;

    @Value("${rtrs.outbox.batch-size:50}")
    private int batchSize;

    // Har 100ms pe outbox table poll kro — unprocessed events Kafka pe jaega
    @Scheduled(fixedDelayString = "${rtrs.outbox.poll-interval-ms:100}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findUnprocessedBatch(batchSize);

        if (pendingEvents.isEmpty()) {
            return;
        }
        log.debug("Outbox batch found. count={}", pendingEvents.size());
        for (OutboxEvent event : pendingEvents) {
            publishEvent(event);
        }
    }

    private void publishEvent(OutboxEvent event) {
        try {
            // Partition key instrument ID hai — same instrument same partition pe jayega
            tradeEventProducer.publish(event.getTopic(), event.getPartitionKey(), event.getPayload())
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            // Kafka publish fail — attempt count badha do, retry next poll mein
                            event.recordFailure(ex.getMessage());
                            outboxEventRepository.save(event);
                            log.error("Kafka publish failed. eventId={}, topic={}, error={}",
                                    event.getId(), event.getTopic(), ex.getMessage());
                        } else {
                            event.markProcessed();
                            outboxEventRepository.save(event);
                            log.debug("Event published successfully. eventId={}, topic={}, partition={}",
                                    event.getId(), event.getTopic(),
                                    result.getRecordMetadata().partition());
                        }
                    });
        } catch (Exception ex) {
            event.recordFailure(ex.getMessage());
            outboxEventRepository.save(event);
            log.error("Unexpected error during Kafka publish. eventId={}, error={}",
                    event.getId(), ex.getMessage());
        }
    }
}