package com.rtrs.amlengineservice.rules;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AmlRuleChain {

    private final List<AmlRule> rules;

    public AmlRuleChain(List<AmlRule> rules) {
        this.rules = rules;
    }

    public AmlChainResult execute(AmlContext context) {
        int riskScore = 0;

        for (AmlRule rule : rules) {
            AmlRuleResult result = rule.evaluate(context);
            if (!result.isPassed()) {
                // Risk score based on rule order — earlier rules = higher severity
                riskScore = 100 - (rules.indexOf(rule) * 10);
                return AmlChainResult.flagged(rule.getRuleName(), result.getFlaggedReason(), riskScore);
            }
            // Each passed rule contributes a small risk score
            riskScore += 5;
        }

        return AmlChainResult.cleared(riskScore);
    }
}
