package com.rtrs.settlementservice.run;

import com.rtrs.settlementservice.clearing.NettedObligation;
import com.rtrs.settlementservice.clearing.NettedObligationRepository;
import com.rtrs.settlementservice.instruction.SettlementInstruction;
import com.rtrs.settlementservice.settlement.SettlementLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private final SettlementLifecycleService settlementLifecycleService;
    private final SettlementRunRepository settlementRunRepository;
    private final NettedObligationRepository obligationRepository;
    private final JobOperator jobOperator;
    private final Job eodSettlementJob;

    // Runs once daily — EOD settlement batch
    @Scheduled(cron = "${rtrs.settlement.eod-cron:0 0 18 * * *}")
    public void runEodSettlement() {
        SettlementRun run = SettlementRun.start();
        settlementRunRepository.save(run);

        try {
            List<SettlementInstruction> dueInstructions = settlementLifecycleService.confirmDueInstructions();

            if (dueInstructions.isEmpty()) {
                run.complete();
                settlementRunRepository.save(run);
                log.info("EOD settlement run completed — no instructions due. runId={}", run.getId());
                return;
            }

            List<NettedObligation> obligations = settlementLifecycleService
                    .clearConfirmedInstructions(dueInstructions, run.getId());

            launchSettlementJob(run.getId(), obligations);

        } catch (Exception ex) {
            run.fail(ex.getMessage());
            settlementRunRepository.save(run);
            log.error("EOD settlement run failed. runId={}, error={}", run.getId(), ex.getMessage(), ex);
        }
    }

    private void launchSettlementJob(UUID runId, List<NettedObligation> obligations) {
        try {
            String obligationIdsCsv = obligations.stream()
                    .map(o -> o.getId().toString())
                    .collect(Collectors.joining(","));

            JobParameters params = new JobParametersBuilder()
                    .addString("settlementRunId", runId.toString())
                    .addString("obligationIds", obligationIdsCsv)
                    .addLong("timestamp", System.currentTimeMillis()) // ensures unique JobInstance per run
                    .toJobParameters();

            JobExecution execution = jobOperator.start(eodSettlementJob, params);

            SettlementRun run = settlementRunRepository.findById(runId)
                    .orElseThrow(() -> new IllegalStateException("SettlementRun not found: " + runId));

            if (execution.getStatus() == BatchStatus.COMPLETED) {
                run.complete();
            } else {
                run.fail("Batch job did not complete. status=" + execution.getStatus());
            }
            settlementRunRepository.save(run);

            log.info("EOD settlement job finished. runId={}, status={}, processed={}, success={}, failed={}",
                    runId, execution.getStatus(), run.getInstructionsProcessed(),
                    run.getSuccessCount(), run.getFailedCount());

        } catch (Exception ex) {
            log.error("Failed to launch EOD settlement job. runId={}, error={}", runId, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }
}
