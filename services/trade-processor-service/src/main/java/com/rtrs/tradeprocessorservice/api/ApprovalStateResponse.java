package com.rtrs.tradeprocessorservice.api;

import com.rtrs.tradeprocessorservice.choreography.TradeApprovalState;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Exposes the two clearance flags separately rather than a single status, because
// that distinction is the whole point of the choreography — risk and AML clear
// independently and in either order
public record ApprovalStateResponse(
        UUID tradeId,
        String instrumentId,
        UUID accountId,
        boolean riskCleared,
        boolean amlCleared,
        Instant riskClearedAt,
        Instant amlClearedAt,
        String status,
        String rejectionReason,
        BigDecimal quantity,
        BigDecimal limitPrice,
        String currency,
        Instant createdAt,
        Instant updatedAt) {

    public static ApprovalStateResponse from(TradeApprovalState state) {
        return new ApprovalStateResponse(
                state.getTradeId(),
                state.getInstrumentId(),
                state.getAccountId(),
                state.isRiskCleared(),
                state.isAmlCleared(),
                state.getRiskClearedAt(),
                state.getAmlClearedAt(),
                state.getStatus(),
                state.getRejectionReason(),
                state.getQuantity(),
                state.getLimitPrice(),
                state.getCurrency(),
                state.getCreatedAt(),
                state.getUpdatedAt());
    }
}
