package com.rtrs.amlengineservice.rules;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Order(1)
public class HighValueTransactionRule implements AmlRule {

    @Value("${rtrs.aml.high-value-threshold-usd}")
    private BigDecimal highValueThreshold;

    @Override
    public String getRuleName() {
        return "HIGH_VALUE_TRANSACTION";
    }

    @Override
    public AmlRuleResult evaluate(AmlContext context) {
        if (context.getNotionalUsd().compareTo(highValueThreshold) > 0) {
            return AmlRuleResult.flag(
                    String.format("Notional USD %.2f exceeds high-value threshold %.2f. SAR filing required.",
                            context.getNotionalUsd(), highValueThreshold)
            );
        }
        return AmlRuleResult.pass();
    }
}