package com.rtrs.amlengineservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AmlEvaluationRepository extends JpaRepository<AmlEvaluationEntity, UUID>{
    boolean existsByTradeId(UUID tradeId);
    int countByAccountIdAndEvaluatedAtAfter(UUID accountId, Instant after);
    Optional<AmlEvaluationEntity> findByTradeId(UUID tradeId);
}
