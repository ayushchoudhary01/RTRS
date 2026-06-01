package com.rtrs.common.exception;

import lombok.Getter;

@Getter
public class DomainException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public DomainException(String message, String errorCode, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}