package com.rtrs.riskengineservice.rules;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class RiskRuleResult {
    private final boolean passed;
    private final String breachReason;

    // The limit the breaching rule actually enforces, carried on the result so
    // RiskBreachedEvent can publish a real thresholdValue instead of a placeholder —
    // only the rule itself knows which of its configured limits was the binding one
    private final BigDecimal thresholdValue;

    public static RiskRuleResult pass() {
        return new RiskRuleResult(true, null, null);
    }

    public static RiskRuleResult breach(String reason, BigDecimal thresholdValue) {
        return new RiskRuleResult(false, reason, thresholdValue);
    }
}
