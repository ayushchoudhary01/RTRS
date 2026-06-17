package com.rtrs.ledgerservice.ledger.read.controller;

import com.rtrs.ledgerservice.ledger.admin.ChainVerificationResult;
import com.rtrs.ledgerservice.ledger.domain.AccountBalance;
import com.rtrs.ledgerservice.ledger.domain.AccountBalanceRepository;
import com.rtrs.ledgerservice.ledger.read.service.LedgerQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ledger")
@RequiredArgsConstructor
public class LedgerAdminController {

    private final LedgerQueryService ledgerQueryService;
    private final AccountBalanceRepository balanceRepository;

    // Verify hash chain integrity for all accounts
    @GetMapping("/integrity/verify-all")
    public ResponseEntity<List<ChainVerificationResult>> verifyAllChains() {
        List<AccountBalance> allBalances = balanceRepository.findAll();

        List<ChainVerificationResult> results = allBalances.stream()
                .map(balance -> {
                    boolean valid = ledgerQueryService.verifyChainIntegrity(balance.getAccountId());
                    return new ChainVerificationResult(
                            balance.getAccountId(),
                            valid,
                            balance.getEntryCount(),
                            balance.getLastEntryHash(),
                            Instant.now(),
                            valid ? null : "Hash chain mismatch detected"
                    );
                })
                .toList();

        return ResponseEntity.ok(results);
    }
}
