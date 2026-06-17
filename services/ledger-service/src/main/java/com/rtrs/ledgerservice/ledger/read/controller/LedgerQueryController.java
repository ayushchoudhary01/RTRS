package com.rtrs.ledgerservice.ledger.read.controller;

import com.rtrs.ledgerservice.ledger.read.dto.AccountBalanceResponse;
import com.rtrs.ledgerservice.ledger.read.dto.LedgerEntryResponse;
import com.rtrs.ledgerservice.ledger.read.service.LedgerQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class LedgerQueryController {

    private final LedgerQueryService ledgerQueryService;

    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<AccountBalanceResponse> getBalance(@PathVariable UUID accountId) {
        return ResponseEntity.ok(ledgerQueryService.getBalance(accountId));
    }

    @GetMapping("/accounts/{accountId}/entries")
    public ResponseEntity<List<LedgerEntryResponse>> getEntriesByAccount(
            @PathVariable UUID accountId) {
        return ResponseEntity.ok(ledgerQueryService.getEntriesByAccount(accountId));
    }

    @GetMapping("/trades/{tradeId}/entries")
    public ResponseEntity<List<LedgerEntryResponse>> getEntriesByTrade(
            @PathVariable UUID tradeId) {
        return ResponseEntity.ok(ledgerQueryService.getEntriesByTrade(tradeId));
    }

    @GetMapping("/accounts/{accountId}/integrity")
    public ResponseEntity<Map<String, Object>> verifyIntegrity(
            @PathVariable UUID accountId) {
        boolean valid = ledgerQueryService.verifyChainIntegrity(accountId);
        return ResponseEntity.ok(Map.of(
                "accountId", accountId.toString(),
                "chainIntact", valid,
                "verifiedAt", Instant.now().toString()
        ));
    }
}
