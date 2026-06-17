package com.rtrs.ledgerservice.kafka;

import com.rtrs.ledgerservice.ledger.write.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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

            UUID tradeId = UUID.fromString(payload.get("tradeId").asString());
            UUID accountId = UUID.fromString(payload.get("accountId").asString());
            String instrumentId = payload.get("instrumentId").asString();
            BigDecimal quantity = new BigDecimal(payload.get("quantity").asString());
            BigDecimal limitPrice = new BigDecimal(payload.get("limitPrice").asString());
            String currency = payload.get("currency").asString();

            ledgerService.recordTrade(tradeId, accountId, instrumentId,
                    quantity, limitPrice, currency);

        } catch (Exception ex) {
            log.error("Failed to process trade.executed event. offset={}, error={}",
                    record.offset(), ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }
}