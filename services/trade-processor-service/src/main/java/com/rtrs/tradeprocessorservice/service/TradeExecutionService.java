package com.rtrs.tradeprocessorservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtrs.tradeprocessorservice.outbox.OutboxEvent;
import com.rtrs.tradeprocessorservice.outbox.OutboxEventRepository;
import com.rtrs.tradeprocessorservice.statemachine.TradeApprovalStatus;
import com.rtrs.tradeprocessorservice.statemachine.TradeEvent;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeExecutionService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final StateMachine<TradeApprovalStatus, TradeEvent> stateMachine;

    @Value("${rtrs.kafka.topics.trade-executed}")
    private String tradeExecutedTopic;

    // Dono approvals aa gye — state machine se execute kro aur outbox me event dalo
    @Transactional
    public void execute(UUID tradeId, String instrumentId) {
        log.info("Executing trade. tradeId={}", tradeId);

        // State machine transition
        stateMachine.sendEvent(Mono.just(
                MessageBuilder.withPayload(TradeEvent.EXECUTE)
                        .setHeader("tradeId", tradeId.toString())
                        .build()
        )).subscribe();

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "tradeId", tradeId.toString(),
                    "executionStatus", "FILLED",
                    "executedAt", Instant.now().toString(),
                    "instrumentId", instrumentId
            ));

            OutboxEvent event = OutboxEvent.create(
                    "Trade",
                    tradeId.toString(),
                    "TradeExecutedEvent",
                    tradeExecutedTopic,
                    instrumentId,
                    payload
            );

            outboxEventRepository.save(event);
            log.info("Trade executed successfully. tradeId={}", tradeId);

        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize trade executed event. tradeId={}", tradeId, ex);
            throw new RuntimeException("Trade execution serialization failed", ex);
        }
    }
}
