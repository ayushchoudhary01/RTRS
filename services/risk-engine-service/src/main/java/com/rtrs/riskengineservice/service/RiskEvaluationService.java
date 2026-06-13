package com.rtrs.riskengineservice.service;

import com.rtrs.events.common.EventMetadata;
import com.rtrs.events.risk.BreachType;
import com.rtrs.events.risk.RiskApprovedEvent;
import com.rtrs.events.risk.RiskBreachedEvent;
import com.rtrs.riskengineservice.domain.RiskEvaluationEntity;
import com.rtrs.riskengineservice.domain.RiskEvaluationRepository;
import com.rtrs.riskengineservice.kafka.RiskEventProducer;
import com.rtrs.riskengineservice.rules.RiskChainResult;
import com.rtrs.riskengineservice.rules.RiskContext;
import com.rtrs.riskengineservice.rules.RiskRuleChain;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskEvaluationService {

    private final RiskRuleChain riskRuleChain;
    private final RiskEvaluationRepository riskEvaluationRepository;
    private final RiskEventProducer riskEventProducer;

    private BreachType mapToBreachType(String ruleName) {
        return switch (ruleName) {
            case "POSITION_LIMIT" -> BreachType.POSITION_LIMIT;
            case "VAR_LIMIT" -> BreachType.VAR_LIMIT;
            case "CONCENTRATION" -> BreachType.CONCENTRATION_LIMIT;
            default -> BreachType.POSITION_LIMIT;
        };
    }

    @Transactional
    public void evaluate(UUID tradeId, String instrumentId, UUID accountId,
                         BigDecimal quantity, BigDecimal limitPrice) {

        // Idempotency — duplicate event aaye toh skip
        if (riskEvaluationRepository.existsByTradeId(tradeId)) {
            log.warn("Risk already evaluated for tradeId={}, skipping", tradeId);
            return;
        }

        BigDecimal notionalUsd = quantity.multiply(limitPrice);
        RiskContext context = new RiskContext(tradeId, instrumentId, accountId,
                quantity, limitPrice, notionalUsd);

        RiskChainResult chainResult = riskRuleChain.execute(context);

        RiskEvaluationEntity evaluation;

        if (chainResult.isApproved()) {
            evaluation = RiskEvaluationEntity.approved(tradeId, instrumentId,
                    accountId, quantity, limitPrice, notionalUsd);
            riskEvaluationRepository.save(evaluation);

            RiskApprovedEvent event = RiskApprovedEvent.newBuilder()
                    .setTradeId(tradeId.toString())
                    .setInstrumentId(instrumentId)
                    .setAccountId(accountId.toString())
                    .setRiskScore(0) // baseline — market-data-service se real score baad mein aayega
                    .setVarContribution(notionalUsd.doubleValue())
                    .setApprovedAt(Instant.now())
                    .setMetadata(EventMetadata.newBuilder()
                            .setEventId(UUID.randomUUID().toString())
                            .setEventVersion(1)
                            .setEventType("RiskApprovedEvent")
                            .setCorrelationId(tradeId.toString())
                            .setCausationId(tradeId.toString())
                            .setSource("risk-engine-service")
                            .setPublishedAt(Instant.now())
                            .build())
                    .build();

            riskEventProducer.publishApproved(event);
            log.info("Trade risk approved. tradeId={}, notionalUsd={}", tradeId, notionalUsd);

        } else {
            evaluation = RiskEvaluationEntity.breached(tradeId, instrumentId,
                    accountId, quantity, limitPrice, notionalUsd,
                    chainResult.getBreachedRule(), chainResult.getBreachReason());
            riskEvaluationRepository.save(evaluation);

            RiskBreachedEvent event = RiskBreachedEvent.newBuilder()
                    .setTradeId(tradeId.toString())
                    .setInstrumentId(instrumentId)
                    .setAccountId(accountId.toString())
                    .setBreachType(mapToBreachType(chainResult.getBreachedRule()))
                    .setBreachValue(notionalUsd.doubleValue())
                    .setThresholdValue(0.0) // threshold value — PositionLimitRule se expose krna padega baad mein
                    .setRiskScore(0)
                    .setReason(chainResult.getBreachReason())
                    .setBreachedAt(Instant.now())
                    .setMetadata(EventMetadata.newBuilder()
                            .setEventId(UUID.randomUUID().toString())
                            .setEventVersion(1)
                            .setEventType("RiskBreachedEvent")
                            .setCorrelationId(tradeId.toString())
                            .setCausationId(tradeId.toString())
                            .setSource("risk-engine-service")
                            .setPublishedAt(Instant.now())
                            .build())
                    .build();

            riskEventProducer.publishBreached(event);
            log.warn("Trade risk breached. tradeId={}, rule={}, reason={}",
                    tradeId, chainResult.getBreachedRule(), chainResult.getBreachReason());
        }
    }
}
