package com.rtrs.amlengineservice.rules;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Order(3)
public class RoundAmountRule implements AmlRule {

    // Round amounts are statistically anomalous in legitimate trading, Real trades have fractional values due to market pricing
    private static final BigDecimal ROUND_AMOUNT_THRESHOLD = BigDecimal.valueOf(50_000);

    @Override
    public String getRuleName() {
        return "ROUND_AMOUNT";
    }

    @Override
    public AmlRuleResult evaluate(AmlContext context) {
        if (context.getNotionalUsd().compareTo(ROUND_AMOUNT_THRESHOLD) < 0) {
            return AmlRuleResult.pass();
        }

        // Check if notional is a multiple of 1000 — suspiciously round
        BigDecimal remainder = context.getNotionalUsd().remainder(BigDecimal.valueOf(1000));
        if (remainder.compareTo(BigDecimal.ZERO) == 0) {
            return AmlRuleResult.flag(
                    String.format("Notional USD %.2f is a suspiciously round amount above $50,000. Possible structuring.",
                            context.getNotionalUsd())
            );
        }
        return AmlRuleResult.pass();
    }
}
