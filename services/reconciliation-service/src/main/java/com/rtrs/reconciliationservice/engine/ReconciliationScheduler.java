package com.rtrs.reconciliationservice.engine;

import com.rtrs.reconciliationservice.domain.ReconciliationRun;
import com.rtrs.reconciliationservice.domain.TradeFact;
import com.rtrs.reconciliationservice.enums.TradeFactStatus;
import com.rtrs.reconciliationservice.repository.ReconciliationRunRepository;
import com.rtrs.reconciliationservice.repository.TradeFactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationScheduler {

    private final TradeFactRepository tradeFactRepository;
    private final ReconciliationEngine reconciliationEngine;
    private final ReconciliationRunRepository runRepository;

    @Value("${rtrs.reconciliation.grace-window-seconds}")
    private long graceWindowSeconds;

    @Scheduled(fixedDelayString = "${rtrs.reconciliation.scheduler.interval-ms}")
    public void runReconciliation() {
        ReconciliationRun run = ReconciliationRun.start();
        runRepository.save(run);

        int tradesChecked = 0;
        int breaksFound = 0;
        int breaksResolved = 0;

        try {
            Instant cutoff = Instant.now().minusSeconds(graceWindowSeconds);

            List<TradeFact> pending = tradeFactRepository
                    .findByStatusAndExecutedAtBefore(TradeFactStatus.PENDING, cutoff);
            List<TradeFact> broken = tradeFactRepository
                    .findByStatusIn(List.of(TradeFactStatus.BROKEN));

            List<TradeFact> toCheck = new ArrayList<>();
            toCheck.addAll(pending);
            toCheck.addAll(broken);

            for (TradeFact fact : toCheck) {
                ReconciliationEngine.TradeCheckResult result =
                        reconciliationEngine.check(fact.getTradeId());
                tradesChecked++;
                breaksFound += result.breaksFound();
                breaksResolved += result.breaksResolved();
            }

            run.complete(tradesChecked, breaksFound, breaksResolved);
            runRepository.save(run);

            log.info("Reconciliation run completed. tradesChecked={}, breaksFound={}, breaksResolved={}",
                    tradesChecked, breaksFound, breaksResolved);

        } catch (Exception ex) {
            log.error("Reconciliation run failed. error={}", ex.getMessage(), ex);
            run.fail(ex.getMessage());
            runRepository.save(run);
        }
    }
}
