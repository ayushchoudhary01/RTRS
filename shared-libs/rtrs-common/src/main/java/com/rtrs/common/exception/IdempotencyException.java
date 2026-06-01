package com.rtrs.common.exception;

public class IdempotencyException extends DomainException {

    public IdempotencyException(String tradeRef) {
        super("Duplicate trade request detected: " + tradeRef, "DUPLICATE_TRADE", 409);
    }
}