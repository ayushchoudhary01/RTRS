package com.rtrs.tradeprocessorservice.choreography;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtrs.tradeprocessorservice.outbox.OutboxEvent;
import com.rtrs.tradeprocessorservice.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompensatingActionService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${rtrs.kafka.topics.trade-rejected}")
    private String tradeRejectedTopic;

    // Trade rejected — compensating event outbox me dalo
    @Transactional
    public void emitTradeRejected(UUID tradeId, String instrumentId, String reason) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "tradeId", tradeId.toString(),
                    "rejectionReason", reason,
                    "rejectedAt", java.time.Instant.now().toString()
            ));

            OutboxEvent event = OutboxEvent.create(
                    "Trade",
                    tradeId.toString(),
                    "TradeRejectedEvent",
                    tradeRejectedTopic,
                    instrumentId,
                    payload
            );

            outboxEventRepository.save(event);
            log.info("Compensating event saved. tradeId={}, reason={}", tradeId, reason);

        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize compensating event. tradeId={}", tradeId, ex);
        }
    }
}