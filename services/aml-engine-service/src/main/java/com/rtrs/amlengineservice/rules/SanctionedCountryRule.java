package com.rtrs.amlengineservice.rules;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(5)
public class SanctionedCountryRule implements AmlRule {

    // OFAC SDN list — sanctioned country currency codes
    // IR=Iran, KP=North Korea, SY=Syria, CU=Cuba, VE=Venezuela
    private final Set<String> sanctionedCurrencies;

    public SanctionedCountryRule(@Value("${rtrs.aml.sanctioned-countries}") String sanctionedCountries) {
        this.sanctionedCurrencies = Arrays.stream(sanctionedCountries.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    @Override
    public String getRuleName() {
        return "SANCTIONED_COUNTRY";
    }

    @Override
    public AmlRuleResult evaluate(AmlContext context) {
        if (sanctionedCurrencies.contains(context.getCurrency().toUpperCase())) {
            return AmlRuleResult.flag(
                    String.format("Currency %s is associated with a sanctioned jurisdiction per OFAC SDN list.",
                            context.getCurrency())
            );
        }
        return AmlRuleResult.pass();
    }
}
