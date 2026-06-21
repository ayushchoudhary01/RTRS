package com.rtrs.settlementservice.outbox;

import com.rtrs.settlementservice.kafka.SettlementEventProducer;
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
    private final SettlementEventProducer settlementEventProducer;

    @Value("${rtrs.outbox.batch-size:50}")
    private int batchSize;

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
            settlementEventProducer.publish(event.getTopic(), event.getPartitionKey(), event.getPayload()).get();
            event.markProcessed();
            outboxEventRepository.save(event);
            log.debug("Event published successfully. eventId={}, topic={}", event.getId(), event.getTopic());
        } catch (Exception ex) {
            event.recordFailure(ex.getMessage());
            outboxEventRepository.save(event);
            log.error("Unexpected error during Kafka publish. eventId={}, error={}",
                    event.getId(), ex.getMessage());
        }
    }
}

