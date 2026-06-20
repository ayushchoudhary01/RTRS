package com.rtrs.reconciliationservice.api;

import com.rtrs.reconciliationservice.domain.ReconciliationRun;
import com.rtrs.reconciliationservice.enums.RunStatus;

import java.time.Instant;
import java.util.UUID;

public record ReconciliationRunResponse(
        UUID id,
        Instant startedAt,
        Instant completedAt,
        int tradesChecked,
        int breaksFound,
        int breaksResolved,
        RunStatus status,
        Long durationMs,
        String failureReason) {

    public static ReconciliationRunResponse from(ReconciliationRun run) {
        return new ReconciliationRunResponse(
                run.getId(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getTradesChecked(),
                run.getBreaksFound(),
                run.getBreaksResolved(),
                run.getStatus(),
                run.getDurationMs(),
                run.getFailureReason());
    }
}
