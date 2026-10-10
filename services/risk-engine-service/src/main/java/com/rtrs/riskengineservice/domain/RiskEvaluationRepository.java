package com.rtrs.riskengineservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface RiskEvaluationRepository extends JpaRepository<RiskEvaluationEntity, UUID> {

    boolean existsByTradeId(UUID tradeId);

    Optional<RiskEvaluationEntity> findByTradeId(UUID tradeId);

    // Accumulated approved exposure for one account in one instrument, used by
    // ConcentrationRule — only APPROVED evaluations count, since a breached trade
    // never executed and so contributes no real position
    @Query("""
            SELECT COALESCE(SUM(r.notionalUsd), 0)
            FROM RiskEvaluationEntity r
            WHERE r.accountId = :accountId
              AND r.instrumentId = :instrumentId
              AND r.outcome = 'APPROVED'
            """)
    BigDecimal sumApprovedNotionalForInstrument(@Param("accountId") UUID accountId,
                                                @Param("instrumentId") String instrumentId);
}
