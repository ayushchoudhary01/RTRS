package com.rtrs.tradeprocessorservice.choreography;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeApprovalAggregator {

    private final ApprovalStateRepository approvalStateRepository;

    // New trade aya — approval state create kro PostgreSQL me
    @Transactional
    public void initiate(UUID tradeId, String instrumentId, UUID accountId,
                         BigDecimal quantity, BigDecimal limitPrice, String currency) {
        if (approvalStateRepository.findByTradeId(tradeId).isPresent()) {
            log.warn("Approval state already exists for tradeId={}", tradeId);
            return;
        }
        TradeApprovalState state = TradeApprovalState.create(tradeId, instrumentId, accountId,
                quantity, limitPrice, currency);
        approvalStateRepository.save(state);
        log.info("Approval state initiated. tradeId={}", tradeId);
    }

    // Risk Engine ne approve kiya — flag set kro
    @Transactional
    public boolean markRiskCleared(UUID tradeId) {
        Optional<TradeApprovalState> stateOpt = approvalStateRepository.findByTradeIdForUpdate(tradeId);
        if (stateOpt.isEmpty()) {
            log.warn("Approval state not yet created for tradeId={}. Risk event arrived before trade submission processed.", tradeId);
            return false;
        }
        TradeApprovalState state = stateOpt.get();
        if ("APPROVED".equals(state.getStatus())) {
            log.warn("Trade already approved, skipping duplicate execution. tradeId={}", tradeId);
            return false;
        }
        state.markRiskCleared();
        approvalStateRepository.save(state);
        log.info("Risk cleared for tradeId={}. bothCleared={}", tradeId, state.isBothCleared());
        return state.isBothCleared();
    }

    // AML Engine ne clear kiya — flag set karo
    @Transactional
    public boolean markAmlCleared(UUID tradeId) {
        Optional<TradeApprovalState> stateOpt = approvalStateRepository.findByTradeIdForUpdate(tradeId);
        if (stateOpt.isEmpty()) {
            log.warn("Approval state not yet created for tradeId={}. AML event arrived before trade submission processed.", tradeId);
            return false;
        }
        TradeApprovalState state = stateOpt.get();
        if ("APPROVED".equals(state.getStatus())) {
            log.warn("Trade already approved, skipping duplicate execution. tradeId={}", tradeId);
            return false;
        }
        state.markAmlCleared();
        approvalStateRepository.save(state);
        log.info("AML cleared for tradeId={}. bothCleared={}", tradeId, state.isBothCleared());
        return state.isBothCleared();
    }

    @Transactional
    public void markRejected(UUID tradeId, String reason) {
        TradeApprovalState state = findOrThrow(tradeId);
        state.markRejected(reason);
        approvalStateRepository.save(state);
        log.warn("Trade rejected. tradeId={}, reason={}", tradeId, reason);
    }

    private TradeApprovalState findOrThrow(UUID tradeId) {
        return approvalStateRepository.findByTradeIdForUpdate(tradeId)
                .orElseThrow(() -> new IllegalStateException("No approval state found for tradeId: " + tradeId));
    }
}