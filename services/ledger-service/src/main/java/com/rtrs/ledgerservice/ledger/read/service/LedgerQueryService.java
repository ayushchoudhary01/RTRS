package com.rtrs.ledgerservice.ledger.read.service;

import com.rtrs.ledgerservice.ledger.domain.*;
import com.rtrs.ledgerservice.ledger.read.dto.AccountBalanceResponse;
import com.rtrs.ledgerservice.ledger.read.dto.LedgerEntryResponse;
import com.rtrs.ledgerservice.ledger.write.HashChainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerQueryService {

    private final AccountBalanceRepository balanceRepository;
    private final LedgerEntryRepository entryRepository;
    private final LedgerJournalRepository journalRepository;
    private final HashChainService hashChainService;

    public AccountBalanceResponse getBalance(UUID accountId) {
        AccountBalance balance = balanceRepository.findByAccountId(accountId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No balance found for accountId: " + accountId));

        return new AccountBalanceResponse(
                balance.getAccountId(),
                balance.getBalance(),
                balance.getCurrency(),
                balance.getEntryCount(),
                balance.getLastEntryHash(),
                balance.getLastEntryAt(),
                balance.getUpdatedAt()
        );
    }

    public List<LedgerEntryResponse> getEntriesByAccount(UUID accountId) {
        return entryRepository.findByAccountIdOrderBySequenceNumAsc(accountId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<LedgerEntryResponse> getEntriesByTrade(UUID tradeId) {
        LedgerJournal journal = journalRepository.findByTradeId(tradeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No ledger journal found for tradeId: " + tradeId));

        return entryRepository.findByJournalIdOrderBySequenceNumAsc(journal.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public boolean verifyChainIntegrity(UUID accountId) {
        List<LedgerEntry> entries = entryRepository
                .findByAccountIdOrderBySequenceNumAsc(accountId);
        boolean valid = hashChainService.verifyChain(entries);
        if (!valid) {
            log.error("Hash chain integrity violation detected for accountId={}", accountId);
        }
        return valid;
    }

    private LedgerEntryResponse toResponse(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getJournalId(),
                entry.getAccountId(),
                entry.getEntryType(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getBalanceAfter(),
                entry.getEntryHash(),
                entry.getPrevHash(),
                entry.getSequenceNum(),
                entry.getCreatedAt()
        );
    }
}
