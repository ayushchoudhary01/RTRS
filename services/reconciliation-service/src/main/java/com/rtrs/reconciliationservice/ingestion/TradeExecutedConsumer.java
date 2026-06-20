package com.rtrs.reconciliationservice.ingestion;

import com.rtrs.reconciliationservice.domain.TradeFact;
import com.rtrs.reconciliationservice.repository.TradeFactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeExecutedConsumer {

    private final TradeFactRepository tradeFactRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${rtrs.kafka.topics.trade-executed}",
            groupId = "reconciliation-group"
    )
    public void consume(ConsumerRecord<String, String> record) {
        log.info("Trade executed event received for reconciliation. offset={}, key={}, partition={}",
                record.offset(), record.key(), record.partition());
        try {
            JsonNode payload = objectMapper.readTree(record.value());

            UUID tradeId = UUID.fromString(payload.get("tradeId").asString());

            if (tradeFactRepository.existsById(tradeId)) {
                log.info("Trade fact already recorded, skipping. tradeId={}", tradeId);
                return;
            }

            UUID accountId = UUID.fromString(payload.get("accountId").asString());
            String instrumentId = payload.get("instrumentId").asString();
            BigDecimal quantity = new BigDecimal(payload.get("quantity").asString());
            BigDecimal limitPrice = new BigDecimal(payload.get("limitPrice").asString());
            String currency = payload.get("currency").asString();
            Instant executedAt = Instant.parse(payload.get("executedAt").asString());

            TradeFact fact = TradeFact.create(
                    tradeId, accountId, instrumentId, quantity, limitPrice, currency, executedAt);

            tradeFactRepository.save(fact);
            log.info("Trade fact recorded. tradeId={}", tradeId);

        } catch (Exception ex) {
            log.error("Failed to process trade.executed event for reconciliation. offset={}, error={}",
                    record.offset(), ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }
}
