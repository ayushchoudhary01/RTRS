package com.rtrs.settlementservice.settlement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtrs.settlementservice.clearing.ClearingService;
import com.rtrs.settlementservice.clearing.NettedObligation;
import com.rtrs.settlementservice.clearing.NettedObligationRepository;
import com.rtrs.settlementservice.enums.SettlementStatus;
import com.rtrs.settlementservice.instruction.SettlementInstruction;
import com.rtrs.settlementservice.instruction.SettlementInstructionRepository;
import com.rtrs.settlementservice.outbox.OutboxEvent;
import com.rtrs.settlementservice.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementLifecycleService {

    private final SettlementInstructionRepository instructionRepository;
    private final NettedObligationRepository obligationRepository;
    private final ClearingService clearingService;
    private final FailedSettlementHandler failedSettlementHandler;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${rtrs.kafka.topics.settlement-settled}")
    private String settlementSettledTopic;

    @Value("${rtrs.settlement.settlement-cycle-days}")
    private int settlementCycleDays;

    // Step 1 of the batch — confirm prerequisites before instructions enter clearing
    @Transactional
    public List<SettlementInstruction> confirmDueInstructions() {
        LocalDate today = LocalDate.now();
        List<SettlementInstruction> due = instructionRepository
                .findByStatusAndSettlementDateLessThanEqual(SettlementStatus.PENDING, today);

        for (SettlementInstruction si : due) {
            si.confirm();
            instructionRepository.save(si);
        }

        log.info("Confirmed {} settlement instructions due for settlement", due.size());
        return due;
    }

    // Step 2 — net confirmed instructions into obligations
    @Transactional
    public List<NettedObligation> clearConfirmedInstructions(List<SettlementInstruction> confirmed, UUID settlementRunId) {
        return clearingService.clearForRun(confirmed, settlementRunId);
    }

    // Step 3 — attempt DVP settlement per obligation
    @Transactional
    public boolean settleObligation(NettedObligation obligation) {
        List<SettlementInstruction> sourceInstructions = clearingService.instructionsForObligation(obligation.getId());

        DvpResult result = clearingService.validateObligation(obligation, sourceInstructions);

        if (result.isPassed()) {
            obligation.markSettled();
            obligationRepository.save(obligation);

            for (SettlementInstruction si : sourceInstructions) {
                si.settle();
                instructionRepository.save(si);
                publishSettled(si);
            }

            log.info("Obligation settled. accountId={}, instrumentId={}, netQty={}",
                    obligation.getAccountId(), obligation.getInstrumentId(), obligation.getNetQuantity());
            return true;

        } else {
            obligation.markFailed();
            obligationRepository.save(obligation);

            for (SettlementInstruction si : sourceInstructions) {
                failedSettlementHandler.handle(si, result.getFailureReason());
            }

            log.warn("Obligation failed DVP check. accountId={}, instrumentId={}, reason={}",
                    obligation.getAccountId(), obligation.getInstrumentId(), result.getFailureReason());
            return false;
        }
    }

    private void publishSettled(SettlementInstruction instruction) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "tradeId", instruction.getTradeId().toString(),
                    "accountId", instruction.getAccountId().toString(),
                    "instrumentId", instruction.getInstrumentId(),
                    "settledAt", Instant.now().toString()
            ));

            OutboxEvent event = OutboxEvent.create(
                    "SettlementInstruction",
                    instruction.getId().toString(),
                    "SettlementSettledEvent",
                    settlementSettledTopic,
                    instruction.getAccountId().toString(),
                    payload
            );
            outboxEventRepository.save(event);

        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize settlement settled event. tradeId={}", instruction.getTradeId(), ex);
        }
    }
}
