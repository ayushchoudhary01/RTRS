package com.rtrs.settlementservice.instruction;

import com.rtrs.settlementservice.enums.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementInstructionRepository extends JpaRepository<SettlementInstruction, UUID> {

    boolean existsByTradeId(UUID tradeId);

    Optional<SettlementInstruction> findByTradeId(UUID tradeId);

    List<SettlementInstruction> findByStatusAndSettlementDateLessThanEqual(
            SettlementStatus status, LocalDate date);

    // Picks up both first-attempt (PENDING) and re-attempt (RETRYING) instructions,
    // so a failed settlement actually gets retried on the next EOD run
    List<SettlementInstruction> findByStatusInAndSettlementDateLessThanEqual(
            List<SettlementStatus> statuses, LocalDate date);

    List<SettlementInstruction> findByAccountIdAndInstrumentIdAndSettlementDateAndStatus(
            UUID accountId, String instrumentId, LocalDate settlementDate, SettlementStatus status);

    List<SettlementInstruction> findByNettedObligationId(UUID nettedObligationId);
}