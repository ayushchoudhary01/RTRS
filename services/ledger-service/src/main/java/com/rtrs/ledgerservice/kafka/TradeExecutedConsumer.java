package com.rtrs.ledgerservice.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtrs.ledgerservice.ledger.write.LedgerService;
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
public class TradeExecutedConsumer {

    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${rtrs.kafka.topics.trade-executed}",
            groupId = "ledger-group"
    )
    public void consume(ConsumerRecord<String, String> record) {
        log.info("Trade executed event received for ledger. offset={}, key={}, partition={}",
                record.offset(), record.key(), record.partition());
        try {
            JsonNode payload = objectMapper.readTree(record.value());

            UUID tradeId = UUID.fromString(payload.get("tradeId").asText());
            UUID accountId = UUID.fromString(payload.get("accountId").asText());
            String instrumentId = payload.get("instrumentId").asText();
            BigDecimal quantity = new BigDecimal(payload.get("quantity").asText());
            BigDecimal limitPrice = new BigDecimal(payload.get("limitPrice").asText());
            String currency = payload.get("currency").asText();

            ledgerService.recordTrade(tradeId, accountId, instrumentId,
                    quantity, limitPrice, currency);

        } catch (Exception ex) {
            log.error("Failed to process trade.executed event. offset={}, error={}",
                    record.offset(), ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }
}