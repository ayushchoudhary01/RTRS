package com.rtrs.tradeprocessorservice.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtrs.tradeprocessorservice.choreography.TradeApprovalAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeSubmittedConsumer {

    private final TradeApprovalAggregator approvalAggregator;

    // consume trade.submitted.v1 — initiate approval state
    @KafkaListener(
            topics = "${rtrs.kafka.topics.trade-submitted}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record) {
        try {
            log.info("Trade submitted event received. key={}, partition={}",
                    record.key(), record.partition());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode payload = mapper.readTree(record.value());

            UUID tradeId = UUID.fromString(payload.get("tradeId").asText());
            String instrumentId = payload.get("instrumentId").asText();
            UUID accountId = UUID.fromString(payload.get("accountId").asText());

            approvalAggregator.initiate(tradeId, instrumentId, accountId);

        } catch (Exception ex) {
            log.error("Failed to process trade.submitted event. key={}, error={}",
                    record.key(), ex.getMessage(), ex);
        }
    }
}
