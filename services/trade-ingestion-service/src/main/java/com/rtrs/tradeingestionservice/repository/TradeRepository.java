package com.rtrs.tradeingestionservice.repository;

import com.rtrs.tradeingestionservice.model.TradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TradeRepository extends JpaRepository<TradeEntity, UUID> {

    boolean existsByClientOrderRef(String clientOrderRef);
    Optional<TradeEntity> findByClientOrderRef(String clientOrderRef);
}