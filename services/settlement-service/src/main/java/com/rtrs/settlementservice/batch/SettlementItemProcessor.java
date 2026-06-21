package com.rtrs.settlementservice.batch;

import com.rtrs.settlementservice.clearing.NettedObligation;
import com.rtrs.settlementservice.settlement.SettlementLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementItemProcessor implements ItemProcessor<NettedObligation, SettlementOutcome> {

    private final SettlementLifecycleService settlementLifecycleService;

    @Override
    public SettlementOutcome process(NettedObligation obligation) {
        boolean success = settlementLifecycleService.settleObligation(obligation);
        return new SettlementOutcome(obligation.getId(), success);
    }
}