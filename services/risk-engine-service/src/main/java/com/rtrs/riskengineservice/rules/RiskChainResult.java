package com.rtrs.riskengineservice.rules;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RiskChainResult {
    private final boolean approved;
    private final String breachedRule;
    private final String breachReason;

    public static RiskChainResult approved() {
        return new RiskChainResult(true, null, null);
    }

    public static RiskChainResult breached(String rule, String reason) {
        return new RiskChainResult(false, rule, reason);
    }
}
