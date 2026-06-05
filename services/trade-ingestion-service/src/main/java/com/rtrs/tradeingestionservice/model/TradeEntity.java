package com.rtrs.tradeingestionservice.model;

import com.rtrs.common.enums.TradeStatus;
import com.rtrs.common.enums.TradeType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trades")
@Getter
@NoArgsConstructor
public class TradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_order_ref", nullable = false, unique = true, updatable = false)
    private String clientOrderRef;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private String instrumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false, updatable = false)
    private TradeType tradeType;

    @Column(nullable = false, updatable = false, precision = 38, scale = 10)
    private BigDecimal quantity;

    @Column(name = "limit_price", nullable = false, updatable = false, precision = 38, scale = 10)
    private BigDecimal limitPrice;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeStatus status;

    @Column(length = 20)
    private String market;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static TradeEntity create(
            String clientOrderRef,
            UUID accountId,
            String instrumentId,
            TradeType tradeType,
            BigDecimal quantity,
            BigDecimal limitPrice,
            String currency,
            String market) {

        TradeEntity trade = new TradeEntity();
        trade.clientOrderRef = clientOrderRef;
        trade.accountId = accountId;
        trade.instrumentId = instrumentId;
        trade.tradeType = tradeType;
        trade.quantity = quantity;
        trade.limitPrice = limitPrice;
        trade.currency = currency;
        trade.market = market;
        trade.status = TradeStatus.PENDING;
        return trade;
    }

    public void transitionStatus(TradeStatus newStatus) {
        validateTransition(this.status, newStatus);
        this.status = newStatus;
    }

    private void validateTransition(TradeStatus current, TradeStatus next) {
        boolean valid = switch (current) {
            case PENDING -> next == TradeStatus.VALIDATED || next == TradeStatus.REJECTED;
            case VALIDATED -> next == TradeStatus.RISK_APPROVED || next == TradeStatus.RISK_REJECTED;
            case RISK_APPROVED -> next == TradeStatus.AML_CLEARED || next == TradeStatus.AML_FLAGGED;
            case AML_CLEARED -> next == TradeStatus.EXECUTED || next == TradeStatus.REJECTED;
            case EXECUTED -> next == TradeStatus.SETTLING;
            case SETTLING -> next == TradeStatus.SETTLED || next == TradeStatus.FAILED;
            default -> false;
        };

        if (!valid) {
            throw new IllegalStateException(
                    "Invalid trade status transition: " + current + " → " + next
            );
        }
    }
}