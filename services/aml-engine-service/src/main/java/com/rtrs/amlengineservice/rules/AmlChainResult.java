package com.rtrs.amlengineservice.rules;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AmlChainResult {
    private final boolean cleared;
    private final String flaggedRule;
    private final String flaggedReason;
    private final int riskScore;

    public static AmlChainResult cleared(int riskScore) {
        return new AmlChainResult(true, null, null, riskScore);
    }

    public static AmlChainResult flagged(String rule, String reason, int riskScore) {
        return new AmlChainResult(false, rule, reason, riskScore);
    }
}
