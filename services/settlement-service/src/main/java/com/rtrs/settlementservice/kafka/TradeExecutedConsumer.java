package com.rtrs.settlementservice.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtrs.settlementservice.instruction.SettlementInstruction;
import com.rtrs.settlementservice.instruction.SettlementInstructionRepository;
import com.rtrs.settlementservice.position.PositionBalance;
import com.rtrs.settlementservice.position.PositionBalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeExecutedConsumer {

    private final SettlementInstructionRepository instructionRepository;
    private final PositionBalanceRepository positionBalanceRepository;
    private final ObjectMapper objectMapper;

    @Value("${rtrs.settlement.settlement-cycle-days}")
    private int settlementCycleDays;

    @KafkaListener(
            topics = "${rtrs.kafka.topics.trade-executed}",
            groupId = "settlement-group"
    )
    @Transactional
    public void consume(ConsumerRecord<String, String> record) {
        log.info("Trade executed event received for settlement. offset={}, key={}, partition={}",
                record.offset(), record.key(), record.partition());
        try {
            JsonNode payload = objectMapper.readTree(record.value());

            UUID tradeId = UUID.fromString(payload.get("tradeId").asText());
            UUID accountId = UUID.fromString(payload.get("accountId").asText());
            String instrumentId = payload.get("instrumentId").asText();
            String tradeType = payload.has("tradeType") ? payload.get("tradeType").asText() : "BUY";
            BigDecimal quantity = new BigDecimal(payload.get("quantity").asText());
            BigDecimal limitPrice = new BigDecimal(payload.get("limitPrice").asText());
            String currency = payload.get("currency").asText();

            if (instructionRepository.existsByTradeId(tradeId)) {
                log.warn("Settlement instruction already exists for tradeId={}, skipping", tradeId);
                return;
            }

            LocalDate settlementDate = LocalDate.now().plusDays(settlementCycleDays);

            SettlementInstruction instruction = SettlementInstruction.create(
                    tradeId, accountId, instrumentId, tradeType,
                    quantity, limitPrice, currency, settlementDate);
            instructionRepository.save(instruction);

            // Position updated immediately on execution, not on settlement
            // mirrors real markets where position exposure exists pre-settlement
            PositionBalance position = positionBalanceRepository
                    .findByAccountIdAndInstrumentIdForUpdate(accountId, instrumentId)
                    .orElseGet(() -> PositionBalance.create(accountId, instrumentId));
            position.applyTrade(tradeType, quantity);
            positionBalanceRepository.save(position);

            log.info("Settlement instruction created. tradeId={}, settlementDate={}",
                    tradeId, settlementDate);

        } catch (Exception ex) {
            log.error("Failed to process trade.executed event for settlement. offset={}, error={}",
                    record.offset(), ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }
}
