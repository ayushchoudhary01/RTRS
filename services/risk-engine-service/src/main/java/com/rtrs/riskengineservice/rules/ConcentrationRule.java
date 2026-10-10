package com.rtrs.riskengineservice.rules;

import com.rtrs.riskengineservice.domain.RiskEvaluationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@Order(3)
public class ConcentrationRule implements RiskRule {

    private final RiskEvaluationRepository riskEvaluationRepository;

    @Value("${rtrs.risk.concentration-limit-percent}")
    private BigDecimal concentrationLimitPercent;

    @Value("${rtrs.risk.portfolio-baseline-usd}")
    private BigDecimal portfolioBaselineUsd;

    public ConcentrationRule(RiskEvaluationRepository riskEvaluationRepository) {
        this.riskEvaluationRepository = riskEvaluationRepository;
    }

    @Override
    public String getRuleName() {
        return "CONCENTRATION";
    }

    // Concentration is about accumulated exposure to one name, not the size of a
    // single trade — checking only this trade's notional made the rule a strictly
    // looser duplicate of PositionLimitRule, since anything large enough to breach
    // it had already breached the per-trade limit; summing the account's
    // already-approved notional for this instrument makes it catch what it is
    // actually meant to catch — a position built up through several
    // individually-acceptable trades
    @Override
    public RiskRuleResult evaluate(RiskContext context) {
        BigDecimal maxAllowed = portfolioBaselineUsd
                .multiply(concentrationLimitPercent)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        BigDecimal existingExposure = riskEvaluationRepository
                .sumApprovedNotionalForInstrument(context.getAccountId(), context.getInstrumentId());

        BigDecimal projectedExposure = existingExposure.add(context.getNotionalUsd());

        if (projectedExposure.compareTo(maxAllowed) > 0) {
            return RiskRuleResult.breach(
                    String.format(
                            "Instrument %s exposure %.2f (existing %.2f + this trade %.2f) exceeds concentration limit %.2f",
                            context.getInstrumentId(), projectedExposure, existingExposure,
                            context.getNotionalUsd(), maxAllowed),
                    maxAllowed
            );
        }
        return RiskRuleResult.pass();
    }
}
