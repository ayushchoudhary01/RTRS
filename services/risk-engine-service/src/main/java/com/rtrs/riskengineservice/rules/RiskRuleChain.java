package com.rtrs.riskengineservice.rules;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RiskRuleChain {

    private final List<RiskRule> rules;

    public RiskRuleChain(List<RiskRule> rules) {
        this.rules = rules;
    }

    public RiskChainResult execute(RiskContext context) {
        for (RiskRule rule : rules) {
            RiskRuleResult result = rule.evaluate(context);
            if (!result.isPassed()) {
                return RiskChainResult.breached(rule.getRuleName(), result.getBreachReason());
            }
        }
        return RiskChainResult.approved();
    }
}
