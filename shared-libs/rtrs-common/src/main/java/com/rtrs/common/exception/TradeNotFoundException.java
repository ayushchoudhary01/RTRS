package com.rtrs.common.exception;

import com.rtrs.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public class TradeNotFoundException extends DomainException {
    public TradeNotFoundException(String tradeId) {
        super(
                "Trade not found: " + tradeId,
                ErrorCode.RESOURCE_NOT_FOUND,
                HttpStatus.NOT_FOUND
        );
    }
}