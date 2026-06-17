package com.rtrs.ledgerservice.ledger.admin;

import com.rtrs.ledgerservice.ledger.domain.AccountBalance;
import com.rtrs.ledgerservice.ledger.domain.AccountBalanceRepository;
import com.rtrs.ledgerservice.ledger.read.service.LedgerQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChainIntegrityScheduler {

    private final LedgerQueryService ledgerQueryService;
    private final AccountBalanceRepository balanceRepository;

    // Nightly at 2 AM — verify all account chains
    @Scheduled(cron = "0 0 2 * * *")
    public void verifyAllChains() {
        log.info("Starting nightly hash chain integrity verification");
        List<AccountBalance> allBalances = balanceRepository.findAll();
        int violations = 0;

        for (AccountBalance balance : allBalances) {
            boolean valid = ledgerQueryService.verifyChainIntegrity(balance.getAccountId());
            if (!valid) {
                violations++;
                log.error("CHAIN INTEGRITY VIOLATION. accountId={}", balance.getAccountId());
                // Future: publish alert event, trigger ops notification
            }
        }

        if (violations == 0) {
            log.info("Nightly chain verification complete. All {} accounts intact.", allBalances.size());
        } else {
            log.error("Nightly chain verification complete. VIOLATIONS FOUND: {}/{}", violations, allBalances.size());
        }
    }
}
