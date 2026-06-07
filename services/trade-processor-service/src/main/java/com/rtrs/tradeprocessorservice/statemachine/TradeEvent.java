package com.rtrs.tradeprocessorservice.statemachine;

public enum TradeEvent {
    RISK_APPROVED,
    RISK_REJECTED,
    AML_CLEARED,
    AML_FLAGGED,
    EXECUTE,
    REJECT,
    TIMEOUT
}