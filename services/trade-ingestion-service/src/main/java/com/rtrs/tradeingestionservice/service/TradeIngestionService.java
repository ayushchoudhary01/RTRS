package com.rtrs.tradeingestionservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtrs.common.enums.TradeStatus;
import com.rtrs.common.exception.DomainException;
import com.rtrs.common.enums.ErrorCode;
import com.rtrs.tradeingestionservice.model.TradeEntity;
import com.rtrs.tradeingestionservice.model.TradeRequest;
import com.rtrs.tradeingestionservice.model.TradeResponse;
import com.rtrs.tradeingestionservice.outbox.OutboxEvent;
import com.rtrs.tradeingestionservice.outbox.OutboxEventRepository;
import com.rtrs.tradeingestionservice.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeIngestionService {

    private final TradeRepository tradeRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdempotencyService idempotencyService;
    private final TradeValidationService validationService;
    private final ObjectMapper objectMapper;

    @Value("${rtrs.kafka.topics.trade-submitted}")
    private String tradeSubmittedTopic;

    // Yhi hai core flow — ek hi transaction mein trade + outbox save hota hai
    @Transactional
    public TradeResponse submitTrade(TradeRequest request) {
        log.info("Trade submission received. clientOrderRef={}, instrumentId={}",
                request.getClientOrderRef(), request.getInstrumentId());

        // Step 1: Idempotency check — duplicate request ho to reject
        idempotencyService.checkAndStore(request.getClientOrderRef(), UUID.randomUUID().toString());

        // Step 2: Business rule validation
        validationService.validate(request);

        // Step 3: Trade entity banao aur save karo
        TradeEntity trade = TradeEntity.create(
                request.getClientOrderRef(),
                UUID.fromString(request.getAccountId()),
                request.getInstrumentId(),
                request.getTradeType(),
                request.getQuantity(),
                request.getLimitPrice(),
                request.getCurrency(),
                request.getMarket()
        );
        trade.transitionStatus(TradeStatus.VALIDATED);
        tradeRepository.save(trade);

        // Step 4: Outbox event banao — same transaction mein save hoga
        // Agar Kafka publish fail ho to bhi trade save rhega aur outbox publisher retry krega
        OutboxEvent outboxEvent = OutboxEvent.create(
                "Trade",
                trade.getId().toString(),
                "TradeSubmittedEvent",
                tradeSubmittedTopic,
                trade.getInstrumentId(), // partition key — same instrument same partition
                serializePayload(trade)
        );
        outboxEventRepository.save(outboxEvent);

        log.info("Trade saved with outbox event. tradeId={}, status={}",
                trade.getId(), trade.getStatus());

        return TradeResponse.from(trade);
    }

    // Trade entity ko JSON mein convert kro outbox payload ke liye
    private String serializePayload(TradeEntity trade) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "tradeId", trade.getId().toString(),
                    "clientOrderRef", trade.getClientOrderRef(),
                    "accountId", trade.getAccountId().toString(),
                    "instrumentId", trade.getInstrumentId(),
                    "tradeType", trade.getTradeType().name(),
                    "quantity", trade.getQuantity(),
                    "limitPrice", trade.getLimitPrice(),
                    "currency", trade.getCurrency(),
                    "status", trade.getStatus().name(),
                    "market", trade.getMarket() != null ? trade.getMarket() : ""
            ));
        } catch (JsonProcessingException ex) {
            throw new DomainException(
                    "Failed to serialize trade payload",
                    ErrorCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}