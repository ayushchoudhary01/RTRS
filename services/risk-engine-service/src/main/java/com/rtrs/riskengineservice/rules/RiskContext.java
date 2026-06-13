package com.rtrs.riskengineservice.rules;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class RiskContext {
    private final UUID tradeId;
    private final String instrumentId;
    private final UUID accountId;
    private final BigDecimal quantity;
    private final BigDecimal limitPrice;
    private final BigDecimal notionalUsd; // pre-calculated before chain starts
}
