package com.rtrs.amlengineservice.service;

import com.rtrs.amlengineservice.domain.AmlEvaluationEntity;
import com.rtrs.amlengineservice.domain.AmlEvaluationRepository;
import com.rtrs.amlengineservice.kafka.AmlEventProducer;
import com.rtrs.amlengineservice.rules.AmlChainResult;
import com.rtrs.amlengineservice.rules.AmlContext;
import com.rtrs.amlengineservice.rules.AmlRuleChain;
import com.rtrs.events.aml.AmlClearedEvent;
import com.rtrs.events.aml.AmlFlaggedEvent;
import com.rtrs.events.common.EventMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AmlEvaluationService {

    private final AmlRuleChain amlRuleChain;
    private final AmlEvaluationRepository amlEvaluationRepository;
    private final AmlEventProducer amlEventProducer;

    @Transactional
    public void evaluate(UUID tradeId, String instrumentId, UUID accountId,
                         BigDecimal quantity, BigDecimal limitPrice, String currency) {

        if (amlEvaluationRepository.existsByTradeId(tradeId)) {
            log.warn("AML already evaluated for tradeId={}, skipping", tradeId);
            return;
        }

        BigDecimal notionalUsd = quantity.multiply(limitPrice);
        AmlContext context = new AmlContext(tradeId, instrumentId, accountId,
                quantity, limitPrice, notionalUsd, currency);

        AmlChainResult chainResult = amlRuleChain.execute(context);

        if (chainResult.isCleared()) {
            AmlEvaluationEntity evaluation = AmlEvaluationEntity.cleared(
                    tradeId, instrumentId, accountId, notionalUsd, chainResult.getRiskScore());
            amlEvaluationRepository.save(evaluation);

            AmlClearedEvent event = AmlClearedEvent.newBuilder()
                    .setTradeId(tradeId.toString())
                    .setAccountId(accountId.toString())
                    .setRiskScore(chainResult.getRiskScore())
                    .setClearedAt(Instant.now())
                    .setMetadata(EventMetadata.newBuilder()
                            .setEventId(UUID.randomUUID().toString())
                            .setEventVersion(1)
                            .setEventType("AmlClearedEvent")
                            .setCorrelationId(tradeId.toString())
                            .setCausationId(tradeId.toString())
                            .setSource("aml-engine-service")
                            .setPublishedAt(Instant.now())
                            .build())
                    .build();

            amlEventProducer.publishCleared(event);
            log.info("Trade AML cleared. tradeId={}, riskScore={}", tradeId, chainResult.getRiskScore());

        } else {
            AmlEvaluationEntity evaluation = AmlEvaluationEntity.flagged(
                    tradeId, instrumentId, accountId, notionalUsd,
                    chainResult.getRiskScore(), chainResult.getFlaggedRule(),
                    chainResult.getFlaggedReason());
            amlEvaluationRepository.save(evaluation);

            AmlFlaggedEvent event = AmlFlaggedEvent.newBuilder()
                    .setTradeId(tradeId.toString())
                    .setAccountId(accountId.toString())
                    .setRiskScore(chainResult.getRiskScore())
                    .setTriggeredRules(List.of(chainResult.getFlaggedRule()))
                    .setFlaggedAt(Instant.now())
                    .setCaseId(null)
                    .setRequiresManualReview(chainResult.getRiskScore() >= 70)
                    .setMetadata(EventMetadata.newBuilder()
                            .setEventId(UUID.randomUUID().toString())
                            .setEventVersion(1)
                            .setEventType("AmlFlaggedEvent")
                            .setCorrelationId(tradeId.toString())
                            .setCausationId(tradeId.toString())
                            .setSource("aml-engine-service")
                            .setPublishedAt(Instant.now())
                            .build())
                    .build();

            amlEventProducer.publishFlagged(event);
            log.warn("Trade AML flagged. tradeId={}, rule={}, riskScore={}",
                    tradeId, chainResult.getFlaggedRule(), chainResult.getRiskScore());
        }
    }
}
