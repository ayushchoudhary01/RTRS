package com.rtrs.amlengineservice.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aml_evaluations")
@Getter
@Setter
@NoArgsConstructor
public class AmlEvaluationEntity {

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
    private BigDecimal notionalUsd;

    @Column(nullable = false)
    private String outcome; // CLEARED | FLAGGED

    private String flaggedReason;
    private String flaggedRule;

    @Column(nullable = false)
    private int riskScore;

    @Column(nullable = false)
    private Instant evaluatedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        evaluatedAt = Instant.now();
        updatedAt = Instant.now();
    }

    public static AmlEvaluationEntity cleared(UUID tradeId, String instrumentId,
                                              UUID accountId, BigDecimal notionalUsd,
                                              int riskScore) {
        AmlEvaluationEntity e = new AmlEvaluationEntity();
        e.tradeId = tradeId;
        e.instrumentId = instrumentId;
        e.accountId = accountId;
        e.notionalUsd = notionalUsd;
        e.outcome = "CLEARED";
        e.riskScore = riskScore;
        return e;
    }

    public static AmlEvaluationEntity flagged(UUID tradeId, String instrumentId,
                                              UUID accountId, BigDecimal notionalUsd,
                                              int riskScore, String flaggedRule,
                                              String reason) {
        AmlEvaluationEntity e = new AmlEvaluationEntity();
        e.tradeId = tradeId;
        e.instrumentId = instrumentId;
        e.accountId = accountId;
        e.notionalUsd = notionalUsd;
        e.outcome = "FLAGGED";
        e.riskScore = riskScore;
        e.flaggedRule = flaggedRule;
        e.flaggedReason = reason;
        return e;
    }
}
