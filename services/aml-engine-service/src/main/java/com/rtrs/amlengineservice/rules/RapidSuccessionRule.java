package com.rtrs.amlengineservice.rules;

import com.rtrs.amlengineservice.domain.AmlEvaluationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@Order(4)
public class RapidSuccessionRule implements AmlRule {

    private final AmlEvaluationRepository amlEvaluationRepository;

    @Value("${rtrs.aml.rapid-succession-window-minutes}")
    private int windowMinutes;

    @Value("${rtrs.aml.rapid-succession-max-trades}")
    private int maxTrades;

    public RapidSuccessionRule(AmlEvaluationRepository amlEvaluationRepository) {
        this.amlEvaluationRepository = amlEvaluationRepository;
    }

    @Override
    public String getRuleName() {
        return "RAPID_SUCCESSION";
    }

    @Override
    public AmlRuleResult evaluate(AmlContext context) {
        Instant windowStart = Instant.now().minus(windowMinutes, ChronoUnit.MINUTES);
        int recentCount = amlEvaluationRepository
                .countByAccountIdAndEvaluatedAtAfter(context.getAccountId(), windowStart);

        if (recentCount >= maxTrades) {
            return AmlRuleResult.flag(
                    String.format("Account %s submitted %d trades in %d minutes. Possible layering pattern.",
                            context.getAccountId(), recentCount + 1, windowMinutes)
            );
        }
        return AmlRuleResult.pass();
    }
}
