package com.rtrs.amlengineservice.rules;

public interface AmlRule {
    String getRuleName();
    AmlRuleResult evaluate(AmlContext context);
}
