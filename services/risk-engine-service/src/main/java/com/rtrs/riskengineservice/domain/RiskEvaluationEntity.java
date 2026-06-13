package com.rtrs.riskengineservice.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_evaluations")
@Getter
@Setter
@NoArgsConstructor
public class RiskEvaluationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID tradeId;

    @Column(nullable = false)
    private String instrumentId;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false, precision = 38, scale = 10)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 38, scale = 10)
    private BigDecimal limitPrice;

    // quantity * limitPrice — rule chain issi pe kam karta hai
    @Column(nullable = false, precision = 38, scale = 10)
    private BigDecimal notionalUsd;

    @Column(nullable = false)
    private String outcome; // APPROVED | BREACHED

    private String breachReason;
    private String breachedRule;

    @Column(nullable = false)
    private Instant evaluatedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        evaluatedAt = Instant.now();
        updatedAt = Instant.now(); // trigger handles updates, but insert needs initial value
    }

    public static RiskEvaluationEntity approved(UUID tradeId, String instrumentId,
                                                UUID accountId, BigDecimal quantity,
                                                BigDecimal limitPrice, BigDecimal notionalUsd) {
        RiskEvaluationEntity e = new RiskEvaluationEntity();
        e.tradeId = tradeId;
        e.instrumentId = instrumentId;
        e.accountId = accountId;
        e.quantity = quantity;
        e.limitPrice = limitPrice;
        e.notionalUsd = notionalUsd;
        e.outcome = "APPROVED";
        return e;
    }

    public static RiskEvaluationEntity breached(UUID tradeId, String instrumentId,
                                                UUID accountId, BigDecimal quantity,
                                                BigDecimal limitPrice, BigDecimal notionalUsd,
                                                String breachedRule, String reason) {
        RiskEvaluationEntity e = new RiskEvaluationEntity();
        e.tradeId = tradeId;
        e.instrumentId = instrumentId;
        e.accountId = accountId;
        e.quantity = quantity;
        e.limitPrice = limitPrice;
        e.notionalUsd = notionalUsd;
        e.outcome = "BREACHED";
        e.breachedRule = breachedRule;
        e.breachReason = reason;
        return e;
    }
}
