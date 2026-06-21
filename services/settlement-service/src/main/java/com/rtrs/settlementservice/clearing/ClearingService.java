package com.rtrs.settlementservice.clearing;

import com.rtrs.settlementservice.instruction.SettlementInstruction;
import com.rtrs.settlementservice.instruction.SettlementInstructionRepository;
import com.rtrs.settlementservice.settlement.DvpResult;
import com.rtrs.settlementservice.settlement.DvpValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClearingService {

    private final SettlementInstructionRepository instructionRepository;
    private final NettedObligationRepository obligationRepository;
    private final DvpValidator dvpValidator;

    // Groups PENDING/CONFIRMED instructions by (account, instrument, settlementDate)
    // and nets them into a single obligation before attempting settlement.
    @Transactional
    public List<NettedObligation> clearForRun(List<SettlementInstruction> dueInstructions, UUID settlementRunId) {

        Map<String, List<SettlementInstruction>> grouped = dueInstructions.stream()
                .collect(Collectors.groupingBy(this::groupKey));

        List<NettedObligation> obligations = new ArrayList<>();

        for (Map.Entry<String, List<SettlementInstruction>> entry : grouped.entrySet()) {
            List<SettlementInstruction> group = entry.getValue();

            for (SettlementInstruction si : group) {
                si.enterClearing(settlementRunId);
                instructionRepository.save(si);
            }

            NettedObligation obligation = NettedObligation.net(
                    group.getFirst().getAccountId(),
                    group.getFirst().getInstrumentId(),
                    group.getFirst().getSettlementDate(),
                    group,
                    settlementRunId
            );
            obligationRepository.save(obligation);

            for (SettlementInstruction si : group) {
                si.assignNettedObligation(obligation.getId());
                instructionRepository.save(si);
            }

            log.info("Netted obligation created. accountId={}, instrumentId={}, netQty={}, netDirection={}, instructionCount={}",
                    obligation.getAccountId(), obligation.getInstrumentId(),
                    obligation.getNetQuantity(), obligation.getNetDirection(),
                    obligation.getInstructionCount());

            obligations.add(obligation);
        }

        return obligations;
    }

    public DvpResult validateObligation(NettedObligation obligation, List<SettlementInstruction> sourceInstructions) {
        return dvpValidator.validate(obligation, sourceInstructions);
    }

    private String groupKey(SettlementInstruction si) {
        return si.getAccountId() + "|" + si.getInstrumentId() + "|" + si.getSettlementDate();
    }

    public List<SettlementInstruction> instructionsForObligation(UUID obligationId) {
        return instructionRepository.findByNettedObligationId(obligationId);
    }
}
