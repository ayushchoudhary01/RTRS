package com.rtrs.settlementservice.instruction;

import com.rtrs.settlementservice.enums.SettlementStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "settlement_instructions")
@Getter
@NoArgsConstructor
public class SettlementInstruction {

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
    private String tradeType; // BUY | SELL

    @Column(nullable = false, updatable = false, precision = 38, scale = 10)
    private BigDecimal quantity;

    @Column(nullable = false, updatable = false, precision = 38, scale = 10)
    private BigDecimal unitPrice;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Column(nullable = false, updatable = false)
    private LocalDate settlementDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status;

    private UUID nettedObligationId;

    private UUID settlementRunId;

    @Column(nullable = false)
    private int retryCount = 0;

    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    public static SettlementInstruction create(UUID tradeId, UUID accountId, String instrumentId,
                                               String tradeType, BigDecimal quantity,
                                               BigDecimal unitPrice, String currency,
                                               LocalDate settlementDate) {
        SettlementInstruction si = new SettlementInstruction();
        si.tradeId = tradeId;
        si.accountId = accountId;
        si.instrumentId = instrumentId;
        si.tradeType = tradeType;
        si.quantity = quantity;
        si.unitPrice = unitPrice;
        si.currency = currency;
        si.settlementDate = settlementDate;
        si.status = SettlementStatus.PENDING;
        return si;
    }

    public void confirm() {
        this.status = SettlementStatus.CONFIRMED;
    }

    public void enterClearing(UUID settlementRunId) {
        this.status = SettlementStatus.CLEARING;
        this.settlementRunId = settlementRunId;
    }

    public void assignNettedObligation(UUID nettedObligationId) {
        this.nettedObligationId = nettedObligationId;
    }

    public void settle() {
        this.status = SettlementStatus.SETTLED;
    }

    public void fail(String reason) {
        this.status = SettlementStatus.FAILED;
        this.failureReason = reason;
        this.retryCount++;
    }

    public void retry() {
        this.status = SettlementStatus.RETRYING;
    }

    public void escalate(String reason) {
        this.status = SettlementStatus.ESCALATED;
        this.failureReason = reason;
    }

    public BigDecimal notionalAmount() {
        return quantity.multiply(unitPrice).setScale(10, RoundingMode.HALF_UP);
    }
}
