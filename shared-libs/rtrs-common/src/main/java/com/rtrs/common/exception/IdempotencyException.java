package com.rtrs.common.exception;

import com.rtrs.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public class IdempotencyException extends DomainException {
    public IdempotencyException(String tradeRef) {
        super(
                "Duplicate trade request detected: " + tradeRef,
                ErrorCode.RESOURCE_ALREADY_EXISTS,
                HttpStatus.CONFLICT
        );
    }
}