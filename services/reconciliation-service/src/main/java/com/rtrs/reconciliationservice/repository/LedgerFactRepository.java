package com.rtrs.reconciliationservice.repository;

import com.rtrs.reconciliationservice.domain.LedgerFact;
import com.rtrs.reconciliationservice.enums.EntryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerFactRepository extends JpaRepository<LedgerFact, UUID> {

    boolean existsByJournalIdAndEntryType(UUID journalId, EntryType entryType);

    List<LedgerFact> findByTradeId(UUID tradeId);
}
