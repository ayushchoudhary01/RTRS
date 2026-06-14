package com.rtrs.amlengineservice.rules;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Order(2)
public class StructuringRule implements AmlRule {

    // Structuring (smurfing) — transactions just below reporting threshold
    private static final BigDecimal REPORTING_THRESHOLD = BigDecimal.valueOf(10_000);
    private static final BigDecimal STRUCTURING_LOWER_BOUND = BigDecimal.valueOf(8_000);

    @Override
    public String getRuleName() {
        return "STRUCTURING";
    }

    @Override
    public AmlRuleResult evaluate(AmlContext context) {
        boolean isJustBelowThreshold = context.getNotionalUsd().compareTo(STRUCTURING_LOWER_BOUND) >= 0
                && context.getNotionalUsd().compareTo(REPORTING_THRESHOLD) < 0;

        if (isJustBelowThreshold) {
            return AmlRuleResult.flag(
                    String.format("Notional USD %.2f falls in structuring band $8,000-$10,000. Possible smurfing pattern.",
                            context.getNotionalUsd())
            );
        }
        return AmlRuleResult.pass();
    }
}
