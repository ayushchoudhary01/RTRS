package com.rtrs.riskengineservice.rules;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RiskRuleResult {
    private final boolean passed;
    private final String breachReason;

    public static RiskRuleResult pass() {
        return new RiskRuleResult(true, null);
    }

    public static RiskRuleResult breach(String reason) {
        return new RiskRuleResult(false, reason);
    }
}
