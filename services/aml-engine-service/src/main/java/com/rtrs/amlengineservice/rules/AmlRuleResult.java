package com.rtrs.amlengineservice.rules;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AmlRuleResult {
    private final boolean passed;
    private final String flaggedReason;

    public static AmlRuleResult pass() {
        return new AmlRuleResult(true, null);
    }

    public static AmlRuleResult flag(String reason) {
        return new AmlRuleResult(false, reason);
    }
}
