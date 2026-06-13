package com.rtrs.riskengineservice.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtrs.riskengineservice.service.RiskEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeSubmittedConsumer {

    private final RiskEvaluationService riskEvaluationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${rtrs.kafka.topics.trade-submitted}",
            groupId = "risk-engine-group"
    )
    public void consume(ConsumerRecord<String, String> record) {
        log.info("Trade received for risk evaluation. offset={}, key={}, partition={}",
                record.offset(), record.key(), record.partition());
        try {
            JsonNode payload = objectMapper.readTree(record.value());

            UUID tradeId = UUID.fromString(payload.get("tradeId").asText());
            String instrumentId = payload.get("instrumentId").asText();
            UUID accountId = UUID.fromString(payload.get("accountId").asText());
            BigDecimal quantity = new BigDecimal(payload.get("quantity").asText());
            BigDecimal limitPrice = new BigDecimal(payload.get("limitPrice").asText());

            riskEvaluationService.evaluate(tradeId, instrumentId, accountId, quantity, limitPrice);

        } catch (Exception ex) {
            log.error("Failed to process trade for risk evaluation. offset={}, error={}",
                    record.offset(), ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }
}
