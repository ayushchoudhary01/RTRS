package com.rtrs.common.dto;

import com.rtrs.common.enums.TradeStatus;
import com.rtrs.common.enums.TradeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

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

    public TradeDto() {}

    public String getTradeId() { return tradeId; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }
    public String getClientOrderRef() { return clientOrderRef; }
    public void setClientOrderRef(String clientOrderRef) { this.clientOrderRef = clientOrderRef; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getInstrumentId() { return instrumentId; }
    public void setInstrumentId(String instrumentId) { this.instrumentId = instrumentId; }
    public TradeType getTradeType() { return tradeType; }
    public void setTradeType(TradeType tradeType) { this.tradeType = tradeType; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public TradeStatus getStatus() { return status; }
    public void setStatus(TradeStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}