package com.rtrs.reconciliationservice.domain;

import com.rtrs.reconciliationservice.enums.TradeFactStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_trade_facts")
@Getter
@NoArgsConstructor
public class TradeFact {

    @Id
    @Column(name = "trade_id", nullable = false, updatable = false)
    private UUID tradeId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private String instrumentId;

    @Column(nullable = false, updatable = false, precision = 38, scale = 10)
    private BigDecimal quantity;

    @Column(name = "limit_price", nullable = false, updatable = false, precision = 38, scale = 10)
    private BigDecimal limitPrice;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Column(name = "executed_at", nullable = false, updatable = false)
    private Instant executedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeFactStatus status = TradeFactStatus.PENDING;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "last_reconciled_at")
    private Instant lastReconciledAt;

    public static TradeFact create(
            UUID tradeId,
            UUID accountId,
            String instrumentId,
            BigDecimal quantity,
            BigDecimal limitPrice,
            String currency,
            Instant executedAt) {

        TradeFact fact = new TradeFact();
        fact.tradeId = tradeId;
        fact.accountId = accountId;
        fact.instrumentId = instrumentId;
        fact.quantity = quantity;
        fact.limitPrice = limitPrice;
        fact.currency = currency;
        fact.executedAt = executedAt;
        fact.status = TradeFactStatus.PENDING;
        fact.receivedAt = Instant.now();
        return fact;
    }

    public void markReconciled() {
        this.status = TradeFactStatus.RECONCILED;
        this.lastReconciledAt = Instant.now();
    }

    public void markBroken() {
        this.status = TradeFactStatus.BROKEN;
        this.lastReconciledAt = Instant.now();
    }
}
