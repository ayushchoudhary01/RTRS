package com.rtrs.tradeprocessorservice.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtrs.tradeprocessorservice.choreography.TradeApprovalAggregator;
import com.rtrs.tradeprocessorservice.service.TradeExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AmlClearedConsumer {

    private final TradeApprovalAggregator approvalAggregator;
    private final TradeExecutionService tradeExecutionService;

    @KafkaListener(
            topics = "${rtrs.kafka.topics.aml-cleared}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode payload = mapper.readTree(record.value());

            UUID tradeId = UUID.fromString(payload.get("tradeId").asText());
            String instrumentId = record.key();

            log.info("AML cleared event received. tradeId={}", tradeId);

            boolean bothCleared = approvalAggregator.markAmlCleared(tradeId);
            if (bothCleared) {
                tradeExecutionService.execute(tradeId, instrumentId);
            }

        } catch (Exception ex) {
            log.error("Failed to process aml.cleared event. error={}", ex.getMessage(), ex);
        }
    }
}
