package com.rtrs.amlengineservice.kafka;

import com.rtrs.events.aml.AmlClearedEvent;
import com.rtrs.events.aml.AmlFlaggedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AmlEventProducer {

    private final KafkaTemplate<String, Object> avroKafkaTemplate;

    @Value("${rtrs.kafka.topics.aml-cleared}")
    private String amlClearedTopic;

    @Value("${rtrs.kafka.topics.aml-flagged}")
    private String amlFlaggedTopic;

    public void publishCleared(AmlClearedEvent event) {
        avroKafkaTemplate.send(amlClearedTopic, event.getTradeId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish AmlCleared. tradeId={}, error={}",
                                event.getTradeId(), ex.getMessage());
                    } else {
                        log.info("AmlCleared published. tradeId={}, partition={}",
                                event.getTradeId(),
                                result.getRecordMetadata().partition());
                    }
                });
    }

    public void publishFlagged(AmlFlaggedEvent event) {
        avroKafkaTemplate.send(amlFlaggedTopic, event.getTradeId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish AmlFlagged. tradeId={}, error={}",
                                event.getTradeId(), ex.getMessage());
                    } else {
                        log.info("AmlFlagged published. tradeId={}, partition={}",
                                event.getTradeId(),
                                result.getRecordMetadata().partition());
                    }
                });
    }
}
