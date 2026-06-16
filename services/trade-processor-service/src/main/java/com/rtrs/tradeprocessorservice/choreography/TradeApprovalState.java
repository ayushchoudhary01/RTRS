package com.rtrs.tradeprocessorservice.choreography;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trade_approval_state")
@Getter
@NoArgsConstructor
public class TradeApprovalState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "trade_id", nullable = false, unique = true, updatable = false)
    private UUID tradeId;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private String instrumentId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "risk_cleared", nullable = false)
    private boolean riskCleared = false;

    @Column(name = "aml_cleared", nullable = false)
    private boolean amlCleared = false;

    @Column(name = "risk_cleared_at")
    private Instant riskClearedAt;

    @Column(name = "aml_cleared_at")
    private Instant amlClearedAt;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "quantity", nullable = false, precision = 38, scale = 10)
    private BigDecimal quantity;

    @Column(name = "limit_price", nullable = false, precision = 38, scale = 10)
    private BigDecimal limitPrice;

    @Column(name = "currency", nullable = false)
    private String currency;

    public static TradeApprovalState create(UUID tradeId, String instrumentId, UUID accountId, BigDecimal quantity,
                                            BigDecimal limitPrice, String currency) {
        TradeApprovalState state = new TradeApprovalState();
        state.tradeId = tradeId;
        state.instrumentId = instrumentId;
        state.accountId = accountId;
        state.status = "PENDING";
        state.quantity = quantity;
        state.limitPrice = limitPrice;
        state.currency = currency;
        return state;
    }

    public void markRiskCleared() {
        this.riskCleared = true;
        this.riskClearedAt = Instant.now();
        updateStatus();
    }

    public void markAmlCleared() {
        this.amlCleared = true;
        this.amlClearedAt = Instant.now();
        updateStatus();
    }

    public void markRejected(String reason) {
        this.status = "REJECTED";
        this.rejectionReason = reason;
    }

    public void markTimeout() {
        this.status = "TIMEOUT";
        this.rejectionReason = "Approval timeout — risk or AML signal not received within 30 seconds";
    }

    public boolean isBothCleared() {
        return riskCleared && amlCleared;
    }

    // Dono signals aa gye to APPROVED, warna current state maintain kro
    private void updateStatus() {
        if (riskCleared && amlCleared) {
            this.status = "APPROVED";
        } else if (riskCleared) {
            this.status = "RISK_CLEARED";
        } else if (amlCleared) {
            this.status = "AML_CLEARED";
        }
    }
}