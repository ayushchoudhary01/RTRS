package com.rtrs.ledgerservice.ledger.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByAccountIdOrderBySequenceNumAsc(UUID accountId);
    List<LedgerEntry> findByJournalIdOrderBySequenceNumAsc(UUID journalId);
    Optional<LedgerEntry> findTopByAccountIdOrderBySequenceNumDesc(UUID accountId);
}
