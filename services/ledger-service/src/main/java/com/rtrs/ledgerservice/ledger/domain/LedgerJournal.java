package com.rtrs.ledgerservice.ledger.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_journals")
@Getter
@NoArgsConstructor
public class LedgerJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID tradeId;

    @Column(nullable = false, updatable = false)
    private UUID accountId;

    @Column(nullable = false, updatable = false)
    private String instrumentId;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Column(nullable = false, updatable = false, precision = 38, scale = 10)
    private BigDecimal quantity;

    @Column(nullable = false, updatable = false, precision = 38, scale = 10)
    private BigDecimal unitPrice;

    @Column(nullable = false, updatable = false, precision = 38, scale = 10)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String status = "POSTED";

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public static LedgerJournal create(UUID tradeId, UUID accountId, String instrumentId,
                                       String currency, BigDecimal quantity,
                                       BigDecimal unitPrice) {
        LedgerJournal j = new LedgerJournal();
        j.tradeId = tradeId;
        j.accountId = accountId;
        j.instrumentId = instrumentId;
        j.currency = currency;
        j.quantity = quantity;
        j.unitPrice = unitPrice;
        j.totalAmount = quantity.multiply(unitPrice).setScale(10, RoundingMode.HALF_UP);
        j.status = "POSTED";
        return j;
    }
}
