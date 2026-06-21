package com.rtrs.settlementservice.position;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "position_balances")
@Getter
@NoArgsConstructor
public class PositionBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private String instrumentId;

    @Column(nullable = false, precision = 38, scale = 10)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public static PositionBalance create(UUID accountId, String instrumentId) {
        PositionBalance pb = new PositionBalance();
        pb.accountId = accountId;
        pb.instrumentId = instrumentId;
        pb.quantity = BigDecimal.ZERO;
        return pb;
    }

    public void applyTrade(String tradeType, BigDecimal quantityDelta) {
        if ("BUY".equals(tradeType)) {
            this.quantity = this.quantity.add(quantityDelta);
        } else {
            this.quantity = this.quantity.subtract(quantityDelta);
        }
    }

    public boolean hasSufficientQuantity(BigDecimal required) {
        return this.quantity.compareTo(required) >= 0;
    }
}
