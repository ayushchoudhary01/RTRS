package com.rtrs.reconciliationservice.domain;

import com.rtrs.reconciliationservice.enums.EntryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "reconciliation_ledger_facts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ledger_fact_journal_entry_type",
                columnNames = {"journal_id", "entry_type"}
        )
)
@Getter
@NoArgsConstructor
public class LedgerFact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "trade_id", nullable = false, updatable = false)
    private UUID tradeId;

    @Column(name = "journal_id", nullable = false, updatable = false)
    private UUID journalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false)
    private EntryType entryType;

    @Column(nullable = false, updatable = false, precision = 38, scale = 10)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Column(name = "ledger_created_at", nullable = false, updatable = false)
    private Instant ledgerCreatedAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    public static LedgerFact create(
            UUID tradeId,
            UUID journalId,
            EntryType entryType,
            BigDecimal amount,
            String currency,
            Instant ledgerCreatedAt) {

        LedgerFact fact = new LedgerFact();
        fact.tradeId = tradeId;
        fact.journalId = journalId;
        fact.entryType = entryType;
        fact.amount = amount;
        fact.currency = currency;
        fact.ledgerCreatedAt = ledgerCreatedAt;
        fact.receivedAt = Instant.now();
        return fact;
    }
}
