package com.rtrs.ledgerservice.ledger.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
@Getter
@NoArgsConstructor
public class LedgerEntry {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID journalId;

    @Column(nullable = false, updatable = false)
    private UUID accountId;

    @Column(nullable = false, updatable = false)
    private String entryType; // DEBIT | CREDIT

    @Column(nullable = false, updatable = false, precision = 38, scale = 10)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Column(nullable = false, updatable = false, precision = 38, scale = 10)
    private BigDecimal balanceAfter;

    @Column(nullable = false, updatable = false, length = 64)
    private String entryHash;

    @Column(nullable = false, updatable = false, length = 64)
    private String prevHash;

    @Column(nullable = false, updatable = false)
    private Long sequenceNum;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;


    public static LedgerEntry create(UUID id, UUID journalId, UUID accountId, String entryType,
                                     BigDecimal amount, String currency, BigDecimal balanceAfter,
                                     String prevHash, String entryHash, Long sequenceNum,
                                     Instant createdAt) {
        LedgerEntry e = new LedgerEntry();
        e.id = id;
        e.createdAt = createdAt;
        e.journalId = journalId;
        e.accountId = accountId;
        e.entryType = entryType;
        e.amount = amount;
        e.currency = currency;
        e.balanceAfter = balanceAfter;
        e.prevHash = prevHash;
        e.entryHash = entryHash;
        e.sequenceNum = sequenceNum;
        return e;
    }
}
