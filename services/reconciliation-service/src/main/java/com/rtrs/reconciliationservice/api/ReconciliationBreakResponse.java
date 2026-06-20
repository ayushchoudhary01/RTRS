package com.rtrs.reconciliationservice.api;

import com.rtrs.reconciliationservice.domain.ReconciliationBreak;
import com.rtrs.reconciliationservice.enums.BreakSeverity;
import com.rtrs.reconciliationservice.enums.BreakStatus;
import com.rtrs.reconciliationservice.enums.BreakType;

import java.time.Instant;
import java.util.UUID;

public record ReconciliationBreakResponse(
        UUID id,
        UUID tradeId,
        BreakType breakType,
        BreakSeverity severity,
        String expectedValue,
        String actualValue,
        BreakStatus status,
        Instant detectedAt,
        Instant resolvedAt) {

    public static ReconciliationBreakResponse from(ReconciliationBreak brk) {
        return new ReconciliationBreakResponse(
                brk.getId(),
                brk.getTradeId(),
                brk.getBreakType(),
                brk.getSeverity(),
                brk.getExpectedValue(),
                brk.getActualValue(),
                brk.getStatus(),
                brk.getDetectedAt(),
                brk.getResolvedAt());
    }
}
