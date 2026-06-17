package com.rtrs.ledgerservice.ledger.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_balances")
@Getter
@Setter
@NoArgsConstructor
public class AccountBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID accountId;

    @Column(nullable = false, precision = 38, scale = 10)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false)
    private String currency = "USD";

    @Column(nullable = false)
    private Long entryCount = 0L;

    @Column(length = 64)
    private String lastEntryHash;

    private Instant lastEntryAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        updatedAt = Instant.now();
    }

    public static AccountBalance create(UUID accountId, String currency) {
        AccountBalance b = new AccountBalance();
        b.accountId = accountId;
        b.balance = BigDecimal.ZERO;
        b.currency = currency;
        b.entryCount = 0L;
        return b;
    }

    public void applyEntry(BigDecimal amount, String entryType, String entryHash) {
        if ("CREDIT".equals(entryType)) {
            this.balance = this.balance.add(amount);
        } else {
            this.balance = this.balance.subtract(amount);
        }
        this.entryCount++;
        this.lastEntryHash = entryHash;
        this.lastEntryAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
