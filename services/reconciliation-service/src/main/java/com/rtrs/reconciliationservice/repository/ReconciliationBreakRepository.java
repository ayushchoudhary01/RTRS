package com.rtrs.reconciliationservice.repository;

import com.rtrs.reconciliationservice.domain.ReconciliationBreak;
import com.rtrs.reconciliationservice.enums.BreakStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReconciliationBreakRepository extends JpaRepository<ReconciliationBreak, UUID> {

    Page<ReconciliationBreak> findByStatus(BreakStatus status, Pageable pageable);

    List<ReconciliationBreak> findByTradeIdAndStatus(UUID tradeId, BreakStatus status);
}