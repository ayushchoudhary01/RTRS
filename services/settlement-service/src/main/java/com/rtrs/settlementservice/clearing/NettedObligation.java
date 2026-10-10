package com.rtrs.settlementservice.clearing;

import com.rtrs.settlementservice.enums.NettedObligationStatus;
import com.rtrs.settlementservice.instruction.SettlementInstruction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "netted_obligations")
@Getter
@NoArgsConstructor
public class NettedObligation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID accountId;

    @Column(nullable = false, updatable = false)
    private String instrumentId;

    @Column(nullable = false, updatable = false)
    private LocalDate settlementDate;

    @Column(nullable = false, updatable = false, precision = 38, scale = 10)
    private BigDecimal netQuantity;

    @Column(nullable = false, updatable = false)
    private String netDirection; // BUY | SELL | FLAT (buys and sells cancelled out exactly)

    @Column(nullable = false, updatable = false)
    private int instructionCount;

    private UUID settlementRunId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NettedObligationStatus status = NettedObligationStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    // Multiple instructions for same account+instrument+date netted into one obligation
    public static NettedObligation net(UUID accountId, String instrumentId,
                                       LocalDate settlementDate,
                                       List<SettlementInstruction> instructions,
                                       UUID settlementRunId) {
        BigDecimal netQty = BigDecimal.ZERO;
        for (SettlementInstruction si : instructions) {
            if ("BUY".equals(si.getTradeType())) {
                netQty = netQty.add(si.getQuantity());
            } else {
                netQty = netQty.subtract(si.getQuantity());
            }
        }

        int sign = netQty.compareTo(BigDecimal.ZERO);

        NettedObligation obligation = new NettedObligation();
        obligation.accountId = accountId;
        obligation.instrumentId = instrumentId;
        obligation.settlementDate = settlementDate;
        obligation.netQuantity = netQty.abs();
        // A genuinely net-zero position is FLAT, not a zero-quantity BUY — there is
        // nothing to deliver and nothing to pay, so DVP has nothing to check
        obligation.netDirection = sign == 0 ? "FLAT" : (sign > 0 ? "BUY" : "SELL");
        obligation.instructionCount = instructions.size();
        obligation.settlementRunId = settlementRunId;
        return obligation;
    }

    public void markSettled() {
        this.status = NettedObligationStatus.SETTLED;
    }

    public void markFailed() {
        this.status = NettedObligationStatus.FAILED;
    }
}
