package com.rtrs.riskengineservice.kafka;

import com.rtrs.events.risk.RiskApprovedEvent;
import com.rtrs.events.risk.RiskBreachedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskEventProducer {

    private final KafkaTemplate<String, Object> avroKafkaTemplate;

    @Value("${rtrs.kafka.topics.risk-approved}")
    private String riskApprovedTopic;

    @Value("${rtrs.kafka.topics.risk-breached}")
    private String riskBreachedTopic;

    public void publishApproved(RiskApprovedEvent event) {
        avroKafkaTemplate.send(riskApprovedTopic, event.getInstrumentId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish RiskApproved. tradeId={}, error={}",
                                event.getTradeId(), ex.getMessage());
                    } else {
                        log.info("RiskApproved published. tradeId={}, partition={}",
                                event.getTradeId(),
                                result.getRecordMetadata().partition());
                    }
                });
    }

    public void publishBreached(RiskBreachedEvent event) {
        avroKafkaTemplate.send(riskBreachedTopic, event.getInstrumentId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish RiskBreached. tradeId={}, error={}",
                                event.getTradeId(), ex.getMessage());
                    } else {
                        log.info("RiskBreached published. tradeId={}, partition={}",
                                event.getTradeId(),
                                result.getRecordMetadata().partition());
                    }
                });
    }
}
