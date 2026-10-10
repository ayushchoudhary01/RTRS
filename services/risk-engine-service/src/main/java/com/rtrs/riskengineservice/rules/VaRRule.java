package com.rtrs.riskengineservice.rules;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@Order(2)
public class VaRRule implements RiskRule {

    // Simplified VaR: a flat percentage of notional stands in for a real
    // volatility-weighted figure — genuine VaR needs historical price data from a
    // market-data service that does not exist yet (docs/future/004)
    @Value("${rtrs.risk.var-threshold-percent}")
    private BigDecimal varThresholdPercent;

    // Absolute VaR tolerance per trade, in USD, compared directly against the
    // computed exposure rather than against a percentage of an assumed portfolio —
    // the previous form made this rule unreachable, because the threshold it
    // derived ($500k of VaR, i.e. $25M of notional) sat far above the $1M
    // per-trade position limit, so PositionLimitRule always breached first
    @Value("${rtrs.risk.var-limit-usd}")
    private BigDecimal varLimitUsd;

    @Override
    public String getRuleName() {
        return "VAR_LIMIT";
    }

    @Override
    public RiskRuleResult evaluate(RiskContext context) {
        BigDecimal varExposure = context.getNotionalUsd()
                .multiply(varThresholdPercent)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        if (varExposure.compareTo(varLimitUsd) > 0) {
            return RiskRuleResult.breach(
                    String.format("VaR exposure %.2f exceeds VaR limit %.2f",
                            varExposure, varLimitUsd),
                    varLimitUsd
            );
        }
        return RiskRuleResult.pass();
    }
}
