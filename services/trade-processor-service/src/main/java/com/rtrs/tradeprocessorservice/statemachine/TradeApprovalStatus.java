package com.rtrs.tradeprocessorservice.statemachine;

public enum TradeApprovalStatus {
    SUBMITTED,
    RISK_CLEARED,
    AML_CLEARED,
    EXECUTING,
    EXECUTED,
    REJECTED
}
