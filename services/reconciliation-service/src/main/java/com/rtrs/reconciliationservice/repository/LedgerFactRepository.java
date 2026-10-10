package com.rtrs.reconciliationservice.repository;

import com.rtrs.reconciliationservice.domain.LedgerFact;
import com.rtrs.reconciliationservice.enums.EntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LedgerFactRepository extends JpaRepository<LedgerFact, UUID> {

    boolean existsByJournalIdAndEntryType(UUID journalId, EntryType entryType);

    List<LedgerFact> findByTradeId(UUID tradeId);

    // Ledger entries with no corresponding trade execution record — the reverse of
    // MISSING_IN_LEDGER — driven from the ledger side because the normal check starts
    // from a TradeFact and therefore cannot see a trade that never executed at all
    @Query("""
            SELECT lf FROM LedgerFact lf
            WHERE lf.receivedAt < :cutoff
              AND NOT EXISTS (SELECT 1 FROM TradeFact tf WHERE tf.tradeId = lf.tradeId)
            """)
    List<LedgerFact> findOrphanedBefore(@Param("cutoff") Instant cutoff);
}
