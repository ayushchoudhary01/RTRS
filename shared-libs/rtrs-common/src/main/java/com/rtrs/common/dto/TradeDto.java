package com.rtrs.common.dto;

import com.rtrs.common.enums.TradeStatus;
import com.rtrs.common.enums.TradeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeDto {

    private String tradeId;

    @NotBlank(message = "Client order reference is required")
    private String clientOrderRef;

    @NotBlank(message = "Account ID is required")
    private String accountId;

    @NotBlank(message = "Instrument ID is required")
    private String instrumentId;

    @NotNull(message = "Trade type is required")
    private TradeType tradeType;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private BigDecimal quantity;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0001", message = "Price must be greater than 0")
    private BigDecimal price;

    private String currency;
    private TradeStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}