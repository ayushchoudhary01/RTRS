package com.rtrs.tradeingestionservice.model;

import com.rtrs.common.enums.TradeStatus;
import com.rtrs.common.enums.TradeType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class TradeResponse {

    private final UUID tradeId;
    private final String clientOrderRef;
    private final String accountId;
    private final String instrumentId;
    private final TradeType tradeType;
    private final BigDecimal quantity;
    private final BigDecimal limitPrice;
    private final String currency;
    private final TradeStatus status;
    private final String market;
    private final Instant createdAt;

    public static TradeResponse from(TradeEntity entity) {
        return TradeResponse.builder()
                .tradeId(entity.getId())
                .clientOrderRef(entity.getClientOrderRef())
                .accountId(entity.getAccountId().toString())
                .instrumentId(entity.getInstrumentId())
                .tradeType(entity.getTradeType())
                .quantity(entity.getQuantity())
                .limitPrice(entity.getLimitPrice())
                .currency(entity.getCurrency())
                .status(entity.getStatus())
                .market(entity.getMarket())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}