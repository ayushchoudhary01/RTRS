package com.rtrs.common.exception;

public class TradeNotFoundException extends DomainException {
    public TradeNotFoundException(String tradeId) {
        super("Trade not found: " + tradeId, "TRADE_NOT_FOUND", 404);
    }
}