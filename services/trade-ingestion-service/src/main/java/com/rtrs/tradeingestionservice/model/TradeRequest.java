package com.rtrs.tradeingestionservice.model;

import com.rtrs.common.enums.TradeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class TradeRequest {

    // Client ka unique order reference — idempotency ke liye use hoga
    @NotBlank(message = "Client order reference is required")
    @Size(max = 100, message = "Client order reference must not exceed 100 characters")
    private String clientOrderRef;

    @NotBlank(message = "Account ID is required")
    private String accountId;

    @NotBlank(message = "Instrument ID is required")
    @Size(max = 50, message = "Instrument ID must not exceed 50 characters")
    private String instrumentId;

    @NotNull(message = "Trade type is required")
    private TradeType tradeType;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private BigDecimal quantity;

    @NotNull(message = "Limit price is required")
    @DecimalMin(value = "0.0001", message = "Limit price must be greater than 0")
    private BigDecimal limitPrice;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO 4217 code")
    private String currency;

    // Market optional hai — default exchange use hoga agar missing ho
    private String market;
}