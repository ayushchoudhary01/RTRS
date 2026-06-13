package com.rtrs.riskengineservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RiskEvaluationRepository extends JpaRepository<RiskEvaluationEntity, UUID> {

    boolean existsByTradeId(UUID tradeId);

    Optional<RiskEvaluationEntity> findByTradeId(UUID tradeId);
}
