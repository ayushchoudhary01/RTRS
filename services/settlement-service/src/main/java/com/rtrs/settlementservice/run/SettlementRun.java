package com.rtrs.settlementservice.run;

import com.rtrs.settlementservice.enums.SettlementRunStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "settlement_runs")
@Getter
@NoArgsConstructor
public class SettlementRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Instant startedAt;

    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementRunStatus status;

    @Column(nullable = false)
    private int instructionsProcessed = 0;

    @Column(nullable = false)
    private int successCount = 0;

    @Column(nullable = false)
    private int failedCount = 0;

    private String failureReason;

    public static SettlementRun start() {
        SettlementRun run = new SettlementRun();
        run.startedAt = Instant.now();
        run.status = SettlementRunStatus.RUNNING;
        return run;
    }

    public void recordOutcome(boolean success) {
        this.instructionsProcessed++;
        if (success) {
            this.successCount++;
        } else {
            this.failedCount++;
        }
    }

    public void complete() {
        this.status = SettlementRunStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void fail(String reason) {
        this.status = SettlementRunStatus.FAILED;
        this.completedAt = Instant.now();
        this.failureReason = reason;
    }
}
