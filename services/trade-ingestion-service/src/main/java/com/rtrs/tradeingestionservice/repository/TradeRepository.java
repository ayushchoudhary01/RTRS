package com.rtrs.tradeingestionservice.repository;

import com.rtrs.tradeingestionservice.model.TradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

// Idempotency is enforced in Redis against clientOrderRef before a trade exists,
// so no clientOrderRef lookups are needed here — see IdempotencyService
public interface TradeRepository extends JpaRepository<TradeEntity, UUID> {
}