package com.rtrs.riskengineservice.rules;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RiskRuleChain {

    private final PositionLimitRule positionLimitRule;
    private final VaRRule varRule;
    private final ConcentrationRule concentrationRule;

    // Order matters — cheapest check pehle, expensive baad mein
    private List<RiskRule> buildChain() {
        return List.of(positionLimitRule, varRule, concentrationRule);
    }

    public RiskChainResult execute(RiskContext context) {
        for (RiskRule rule : buildChain()) {
            RiskRuleResult result = rule.evaluate(context);
            if (!result.isPassed()) {
                return RiskChainResult.breached(rule.getRuleName(), result.getBreachReason());
            }
        }
        return RiskChainResult.approved();
    }
}
