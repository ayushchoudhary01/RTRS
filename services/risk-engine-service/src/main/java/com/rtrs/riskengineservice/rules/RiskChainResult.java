package com.rtrs.riskengineservice.rules;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class RiskChainResult {
    private final boolean approved;
    private final String breachedRule;
    private final String breachReason;
    private final BigDecimal thresholdValue;

    public static RiskChainResult approved() {
        return new RiskChainResult(true, null, null, null);
    }

    public static RiskChainResult breached(String rule, String reason, BigDecimal thresholdValue) {
        return new RiskChainResult(false, rule, reason, thresholdValue);
    }
}
