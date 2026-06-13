package com.rtrs.riskengineservice.rules;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class VaRRule implements RiskRule {

    // Simplified VaR — notional ka percentage check
    // Real VaR ke liye historical price data chahiye hoga (market-data-service se)
    @Value("${rtrs.risk.var-threshold-percent}")
    private BigDecimal varThresholdPercent;

    @Override
    public String getRuleName() {
        return "VAR_LIMIT";
    }

    @Override
    public RiskRuleResult evaluate(RiskContext context) {
        BigDecimal varExposure = context.getNotionalUsd()
                .multiply(varThresholdPercent)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        // 10M portfolio assume kiya hai baseline ke liye
        BigDecimal portfolioBaseline = BigDecimal.valueOf(10_000_000);

        if (varExposure.compareTo(portfolioBaseline.multiply(BigDecimal.valueOf(0.05))) > 0) {
            return RiskRuleResult.breach(
                    String.format("VaR exposure %.2f exceeds 5%% of portfolio baseline",
                            varExposure)
            );
        }
        return RiskRuleResult.pass();
    }
}
