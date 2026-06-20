package com.rtrs.reconciliationservice.domain;

import com.rtrs.reconciliationservice.enums.RunStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_runs")
@Getter
@NoArgsConstructor
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "trades_checked", nullable = false)
    private int tradesChecked = 0;

    @Column(name = "breaks_found", nullable = false)
    private int breaksFound = 0;

    @Column(name = "breaks_resolved", nullable = false)
    private int breaksResolved = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "failure_reason")
    private String failureReason;

    public static ReconciliationRun start() {
        ReconciliationRun run = new ReconciliationRun();
        run.startedAt = Instant.now();
        run.status = RunStatus.RUNNING;
        return run;
    }

    public void complete(int tradesChecked, int breaksFound, int breaksResolved) {
        this.tradesChecked = tradesChecked;
        this.breaksFound = breaksFound;
        this.breaksResolved = breaksResolved;
        this.completedAt = Instant.now();
        this.durationMs = this.completedAt.toEpochMilli() - this.startedAt.toEpochMilli();
        this.status = RunStatus.COMPLETED;
    }

    public void fail(String reason) {
        this.completedAt = Instant.now();
        this.durationMs = this.completedAt.toEpochMilli() - this.startedAt.toEpochMilli();
        this.status = RunStatus.FAILED;
        this.failureReason = reason;
    }
}
