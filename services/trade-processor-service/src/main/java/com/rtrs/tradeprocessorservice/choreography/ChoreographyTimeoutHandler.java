package com.rtrs.tradeprocessorservice.choreography;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChoreographyTimeoutHandler {

    private final ApprovalStateRepository approvalStateRepository;
    private final CompensatingActionService compensatingActionService;

    // Check every 10 sec — 30 second se zyada purane PENDING trades timeout ho jayenge
    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void handleStaleApprovals() {
        Instant cutoff = Instant.now().minusSeconds(30);
        List<TradeApprovalState> staleApprovals = approvalStateRepository.findStaleApprovals(cutoff);

        if (staleApprovals.isEmpty()) {
            return;
        }

        log.warn("Stale approvals found. count={}", staleApprovals.size());

        for (TradeApprovalState state : staleApprovals) {
            state.markTimeout();
            approvalStateRepository.save(state);
            compensatingActionService.emitTradeRejected(
                    state.getTradeId(),
                    state.getInstrumentId(),
                    "TIMEOUT"
            );
            log.warn("Trade timed out and rejected. tradeId={}", state.getTradeId());
        }
    }
}