package com.rtrs.ledgerservice.ledger.write;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.rtrs.ledgerservice.ledger.domain.*;
import com.rtrs.ledgerservice.outbox.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerJournalRepository journalRepository;
    private final LedgerEntryRepository entryRepository;
    private final AccountBalanceRepository balanceRepository;
    private final HashChainService hashChainService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${rtrs.kafka.topics.ledger-entry-created}")
    private String ledgerEntryCreatedTopic;

    @Transactional
    public void recordTrade(UUID tradeId, UUID accountId, String instrumentId,
                            BigDecimal quantity, BigDecimal limitPrice, String currency) {

        // Idempotency — duplicate trade.executed.v1 must not create duplicate journal
        if (journalRepository.existsByTradeId(tradeId)) {
            log.warn("Ledger entry already exists for tradeId={}, skipping", tradeId);
            return;
        }

        BigDecimal totalAmount = quantity.multiply(limitPrice).setScale(10, RoundingMode.HALF_UP);

        // Create journal — links the debit and credit pair
        LedgerJournal journal = LedgerJournal.create(tradeId, accountId, instrumentId,
                currency, quantity, limitPrice);
        journalRepository.save(journal);

        // Pessimistic lock on account balance — serializes hash chain + balance updates
        AccountBalance balance = balanceRepository.findByAccountIdForUpdate(accountId)
                .orElseGet(() -> AccountBalance.create(accountId, currency));

        String prevHash = balance.getLastEntryHash() != null
                ? balance.getLastEntryHash()
                : hashChainService.getGenesisHash();

        Long nextSeq = balance.getEntryCount();

        // DEBIT entry — securities outflow (buyer pays)
        UUID debitId = UUID.randomUUID();
        Instant debitTime = Instant.now();
        BigDecimal balanceAfterDebit = balance.getBalance().subtract(totalAmount);
        String debitHash = hashChainService.computeHash(prevHash, debitId, accountId,
                "DEBIT", totalAmount, debitTime);

        LedgerEntry debitEntry = LedgerEntry.create(journal.getId(), accountId, "DEBIT",
                totalAmount, currency, balanceAfterDebit, prevHash, debitHash, nextSeq);
        entryRepository.save(debitEntry);
        balance.applyEntry(totalAmount, "DEBIT", debitHash);
        balanceRepository.save(balance);

        // CREDIT entry — securities inflow (buyer receives securities)
        UUID creditId = UUID.randomUUID();
        Instant creditTime = Instant.now();
        BigDecimal balanceAfterCredit = balance.getBalance().add(totalAmount);
        String creditHash = hashChainService.computeHash(debitHash, creditId, accountId,
                "CREDIT", totalAmount, creditTime);

        LedgerEntry creditEntry = LedgerEntry.create(journal.getId(), accountId, "CREDIT",
                totalAmount, currency, balanceAfterCredit, debitHash, creditHash, nextSeq + 1);
        entryRepository.save(creditEntry);
        balance.applyEntry(totalAmount, "CREDIT", creditHash);
        balanceRepository.save(balance);

        // Publish both entries to outbox
        publishToOutbox(debitEntry, journal, tradeId);
        publishToOutbox(creditEntry, journal, tradeId);

        log.info("Double-entry ledger posted. tradeId={}, journalId={}, totalAmount={}",
                tradeId, journal.getId(), totalAmount);
    }

    private void publishToOutbox(LedgerEntry entry, LedgerJournal journal, UUID tradeId) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "entryId", entry.getId().toString(),
                    "tradeId", tradeId.toString(),
                    "accountId", entry.getAccountId().toString(),
                    "journalId", journal.getId().toString(),
                    "entryType", entry.getEntryType(),
                    "amount", entry.getAmount().toPlainString(),
                    "currency", entry.getCurrency(),
                    "balanceAfter", entry.getBalanceAfter().toPlainString(),
                    "entryHash", entry.getEntryHash(),
                    "createdAt", entry.getCreatedAt().toString()
            ));

            OutboxEvent outboxEvent = OutboxEvent.create(
                    "LedgerEntry",
                    entry.getId().toString(),
                    "LedgerEntryCreatedEvent",
                    ledgerEntryCreatedTopic,
                    journal.getAccountId().toString(),
                    payload
            );
            outboxEventRepository.save(outboxEvent);

        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize ledger outbox event", ex);
        }
    }
}
