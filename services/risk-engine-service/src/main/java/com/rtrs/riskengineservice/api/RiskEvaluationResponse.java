package com.rtrs.riskengineservice.api;

import com.rtrs.riskengineservice.domain.RiskEvaluationEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RiskEvaluationResponse(
        UUID tradeId,
        String instrumentId,
        UUID accountId,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal notionalUsd,
        String outcome,
        String breachedRule,
        String breachReason,
        Instant evaluatedAt) {

    public static RiskEvaluationResponse from(RiskEvaluationEntity e) {
        return new RiskEvaluationResponse(
                e.getTradeId(),
                e.getInstrumentId(),
                e.getAccountId(),
                e.getQuantity(),
                e.getLimitPrice(),
                e.getNotionalUsd(),
                e.getOutcome(),
                e.getBreachedRule(),
                e.getBreachReason(),
                e.getEvaluatedAt());
    }
}
