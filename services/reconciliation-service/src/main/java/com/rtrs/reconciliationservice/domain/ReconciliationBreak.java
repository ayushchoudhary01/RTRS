package com.rtrs.reconciliationservice.domain;

import com.rtrs.reconciliationservice.enums.BreakSeverity;
import com.rtrs.reconciliationservice.enums.BreakStatus;
import com.rtrs.reconciliationservice.enums.BreakType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_breaks")
@Getter
@NoArgsConstructor
public class ReconciliationBreak {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "trade_id", nullable = false, updatable = false)
    private UUID tradeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "break_type", nullable = false, updatable = false)
    private BreakType breakType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private BreakSeverity severity;

    @Column(name = "expected_value", updatable = false)
    private String expectedValue;

    @Column(name = "actual_value", updatable = false)
    private String actualValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BreakStatus status;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public static ReconciliationBreak raise(
            UUID tradeId,
            BreakType breakType,
            BreakSeverity severity,
            String expectedValue,
            String actualValue) {

        ReconciliationBreak brk = new ReconciliationBreak();
        brk.tradeId = tradeId;
        brk.breakType = breakType;
        brk.severity = severity;
        brk.expectedValue = expectedValue;
        brk.actualValue = actualValue;
        brk.status = BreakStatus.OPEN;
        brk.detectedAt = Instant.now();
        return brk;
    }

    public void resolve() {
        this.status = BreakStatus.RESOLVED;
        this.resolvedAt = Instant.now();
    }
}
