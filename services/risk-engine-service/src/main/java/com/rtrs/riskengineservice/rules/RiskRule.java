package com.rtrs.riskengineservice.rules;

public interface RiskRule {

    // Rule ka naam — breach log mein jaayega
    String getRuleName();

    // false = breach, true = pass
    RiskRuleResult evaluate(RiskContext context);
}
