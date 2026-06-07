package com.rtrs.tradeprocessorservice.choreography;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalStateRepository extends JpaRepository<TradeApprovalState, UUID> {

    Optional<TradeApprovalState> findByTradeId(UUID tradeId);

    // 30 seconds ke baad bhi PENDING hai to timeout candidate hai
    @Query("SELECT t FROM TradeApprovalState t WHERE t.status IN ('PENDING', 'RISK_CLEARED', 'AML_CLEARED') AND t.createdAt < :cutoff")
    List<TradeApprovalState> findStaleApprovals(Instant cutoff);
}