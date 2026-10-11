package com.rtrs.amlengineservice.api;

import com.rtrs.amlengineservice.domain.AmlEvaluationEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AmlEvaluationResponse(
        UUID tradeId,
        String instrumentId,
        UUID accountId,
        BigDecimal notionalUsd,
        String outcome,
        String flaggedRule,
        String flaggedReason,
        int riskScore,
        boolean requiresManualReview,
        Instant evaluatedAt) {

    // requiresManualReview is derived the same way AmlEvaluationService derives it
    // for the published event, so the API and the Kafka event never disagree
    private static final int MANUAL_REVIEW_THRESHOLD = 70;

    public static AmlEvaluationResponse from(AmlEvaluationEntity e) {
        boolean flagged = "FLAGGED".equals(e.getOutcome());
        return new AmlEvaluationResponse(
                e.getTradeId(),
                e.getInstrumentId(),
                e.getAccountId(),
                e.getNotionalUsd(),
                e.getOutcome(),
                e.getFlaggedRule(),
                e.getFlaggedReason(),
                e.getRiskScore(),
                flagged && e.getRiskScore() >= MANUAL_REVIEW_THRESHOLD,
                e.getEvaluatedAt());
    }
}
