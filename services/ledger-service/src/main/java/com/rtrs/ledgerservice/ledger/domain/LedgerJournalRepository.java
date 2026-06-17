package com.rtrs.ledgerservice.ledger.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerJournalRepository extends JpaRepository<LedgerJournal, UUID> {
    boolean existsByTradeId(UUID tradeId);
    Optional<LedgerJournal> findByTradeId(UUID tradeId);
}