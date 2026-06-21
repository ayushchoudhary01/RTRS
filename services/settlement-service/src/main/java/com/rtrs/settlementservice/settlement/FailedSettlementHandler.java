package com.rtrs.settlementservice.settlement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtrs.settlementservice.instruction.SettlementInstruction;
import com.rtrs.settlementservice.instruction.SettlementInstructionRepository;
import com.rtrs.settlementservice.kafka.SettlementEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailedSettlementHandler {

    private final SettlementInstructionRepository instructionRepository;
    private final SettlementEventProducer settlementEventProducer;
    private final ObjectMapper objectMapper;

    @Value("${rtrs.settlement.max-retry-attempts}")
    private int maxRetryAttempts;

    @Value("${rtrs.kafka.topics.settlement-failed}")
    private String settlementFailedTopic;

    @Value("${rtrs.kafka.topics.settlement-escalated}")
    private String settlementEscalatedTopic;

    @Transactional
    public void handle(SettlementInstruction instruction, String reason) {
        instruction.fail(reason);

        if (instruction.getRetryCount() >= maxRetryAttempts) {
            instruction.escalate(reason);
            instructionRepository.save(instruction);
            publishEscalated(instruction, reason);
            log.error("Settlement escalated after {} retries. tradeId={}, reason={}",
                    maxRetryAttempts, instruction.getTradeId(), reason);
        } else {
            instruction.retry();
            instructionRepository.save(instruction);
            publishFailed(instruction, reason);
            log.warn("Settlement failed, will retry. tradeId={}, attempt={}/{}, reason={}",
                    instruction.getTradeId(), instruction.getRetryCount(), maxRetryAttempts, reason);
        }
    }

    private void publishFailed(SettlementInstruction instruction, String reason) {
        publish(settlementFailedTopic, instruction, reason);
    }

    private void publishEscalated(SettlementInstruction instruction, String reason) {
        publish(settlementEscalatedTopic, instruction, reason);
    }

    private void publish(String topic, SettlementInstruction instruction, String reason) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "tradeId", instruction.getTradeId().toString(),
                    "accountId", instruction.getAccountId().toString(),
                    "instrumentId", instruction.getInstrumentId(),
                    "status", instruction.getStatus().toString(),
                    "retryCount", instruction.getRetryCount(),
                    "reason", reason,
                    "occurredAt", Instant.now().toString()
            ));
            settlementEventProducer.publish(topic, instruction.getAccountId().toString(), payload);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize settlement failure event. tradeId={}", instruction.getTradeId(), ex);
        }
    }
}
