package com.rtrs.riskengineservice.rules;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ConcentrationRule implements RiskRule {

    @Value("${rtrs.risk.concentration-limit-percent}")
    private BigDecimal concentrationLimitPercent;

    @Override
    public String getRuleName() {
        return "CONCENTRATION";
    }

    @Override
    public RiskRuleResult evaluate(RiskContext context) {
        // Single instrument mein portfolio ka max X% allowed hai
        BigDecimal portfolioBaseline = BigDecimal.valueOf(10_000_000);
        BigDecimal maxAllowed = portfolioBaseline
                .multiply(concentrationLimitPercent)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        if (context.getNotionalUsd().compareTo(maxAllowed) > 0) {
            return RiskRuleResult.breach(
                    String.format("Instrument %s concentration %.2f exceeds limit %.2f",
                            context.getInstrumentId(), context.getNotionalUsd(), maxAllowed)
            );
        }
        return RiskRuleResult.pass();
    }
}
