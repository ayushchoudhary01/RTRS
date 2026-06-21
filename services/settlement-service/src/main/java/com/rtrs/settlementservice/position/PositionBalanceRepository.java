package com.rtrs.settlementservice.position;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PositionBalanceRepository extends JpaRepository<PositionBalance, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PositionBalance p WHERE p.accountId = :accountId AND p.instrumentId = :instrumentId")
    Optional<PositionBalance> findByAccountIdAndInstrumentIdForUpdate(
            @Param("accountId") UUID accountId, @Param("instrumentId") String instrumentId);

    Optional<PositionBalance> findByAccountIdAndInstrumentId(UUID accountId, String instrumentId);
}
