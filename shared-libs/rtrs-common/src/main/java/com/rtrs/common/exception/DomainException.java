package com.rtrs.common.exception;

import com.rtrs.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public class DomainException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    public DomainException(String message, ErrorCode errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public HttpStatus getHttpStatus() { return httpStatus; }
}