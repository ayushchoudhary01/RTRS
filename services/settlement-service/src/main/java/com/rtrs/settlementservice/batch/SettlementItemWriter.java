package com.rtrs.settlementservice.batch;

import com.rtrs.settlementservice.run.SettlementRun;
import com.rtrs.settlementservice.run.SettlementRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class SettlementItemWriter implements ItemWriter<SettlementOutcome> {

    private final SettlementRunRepository settlementRunRepository;
    private final UUID settlementRunId;

    @Override
    public void write(Chunk<? extends SettlementOutcome> chunk) {
        SettlementRun run = settlementRunRepository.findById(settlementRunId)
                .orElseThrow(() -> new IllegalStateException("SettlementRun not found: " + settlementRunId));

        for (SettlementOutcome outcome : chunk) {
            run.recordOutcome(outcome.success());
        }
        settlementRunRepository.save(run);
    }
}