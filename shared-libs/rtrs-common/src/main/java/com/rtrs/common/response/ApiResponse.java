package com.rtrs.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final String errorCode;
    private final Instant timestamp;

    private ApiResponse(Builder<T> builder) {
        this.success = builder.success;
        this.message = builder.message;
        this.data = builder.data;
        this.errorCode = builder.errorCode;
        this.timestamp = Instant.now();
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public String getErrorCode() { return errorCode; }
    public Instant getTimestamp() { return timestamp; }

    public static <T> ApiResponse<T> ok(T data) {
        return new Builder<T>().success(true).data(data).build();
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new Builder<T>().success(true).data(data).message(message).build();
    }

    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return new Builder<T>().success(false).message(message).errorCode(errorCode).build();
    }

    public static class Builder<T> {
        private boolean success;
        private String message;
        private T data;
        private String errorCode;

        public Builder<T> success(boolean success) { this.success = success; return this; }
        public Builder<T> message(String message) { this.message = message; return this; }
        public Builder<T> data(T data) { this.data = data; return this; }
        public Builder<T> errorCode(String errorCode) { this.errorCode = errorCode; return this; }
        public ApiResponse<T> build() { return new ApiResponse<>(this); }
    }
}