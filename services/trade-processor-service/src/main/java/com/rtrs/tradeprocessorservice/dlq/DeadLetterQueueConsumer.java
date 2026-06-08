package com.rtrs.tradeprocessorservice.dlq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterQueueConsumer {

    private final DlqAlertPublisher dlqAlertPublisher;

    // DLQ se failed events consume kro — retry ke baad bhi fail hua toh alert bhejo
    @KafkaListener(
            topics = "${rtrs.kafka.topics.trade-submitted}.DLT",
            groupId = "${spring.kafka.consumer.group-id}-dlq"
    )
    public void consume(ConsumerRecord<String, String> record) {
        log.error("DLQ event received. topic={}, key={}, partition={}",
                record.topic(), record.key(), record.partition());
        dlqAlertPublisher.alert(record.key(), record.topic(), record.value());
    }
}
