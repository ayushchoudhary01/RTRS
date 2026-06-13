package com.rtrs.riskengineservice.rules;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PositionLimitRule implements RiskRule {

    @Value("${rtrs.risk.position-limit-usd}")
    private BigDecimal positionLimitUsd;

    @Override
    public String getRuleName() {
        return "POSITION_LIMIT";
    }

    @Override
    public RiskRuleResult evaluate(RiskContext context) {
        if (context.getNotionalUsd().compareTo(positionLimitUsd) > 0) {
            return RiskRuleResult.breach(
                    String.format("Notional USD %.2f exceeds position limit %.2f",
                            context.getNotionalUsd(), positionLimitUsd)
            );
        }
        return RiskRuleResult.pass();
    }
}
