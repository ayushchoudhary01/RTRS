package com.rtrs.reconciliationservice.repository;

import com.rtrs.reconciliationservice.domain.TradeFact;
import com.rtrs.reconciliationservice.enums.TradeFactStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TradeFactRepository extends JpaRepository<TradeFact, UUID> {

    List<TradeFact> findByStatusAndExecutedAtBefore(TradeFactStatus status, Instant cutoff);

    List<TradeFact> findByStatusIn(List<TradeFactStatus> statuses);
}
