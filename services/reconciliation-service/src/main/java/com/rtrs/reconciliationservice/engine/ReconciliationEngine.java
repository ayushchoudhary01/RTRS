package com.rtrs.reconciliationservice.engine;

import com.rtrs.reconciliationservice.domain.LedgerFact;
import com.rtrs.reconciliationservice.domain.ReconciliationBreak;
import com.rtrs.reconciliationservice.domain.TradeFact;
import com.rtrs.reconciliationservice.enums.BreakSeverity;
import com.rtrs.reconciliationservice.enums.BreakStatus;
import com.rtrs.reconciliationservice.enums.BreakType;
import com.rtrs.reconciliationservice.enums.EntryType;
import com.rtrs.reconciliationservice.repository.LedgerFactRepository;
import com.rtrs.reconciliationservice.repository.ReconciliationBreakRepository;
import com.rtrs.reconciliationservice.repository.TradeFactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationEngine {

    private final TradeFactRepository tradeFactRepository;
    private final LedgerFactRepository ledgerFactRepository;
    private final ReconciliationBreakRepository breakRepository;

    @Value("${rtrs.reconciliation.grace-window-seconds}")
    private long graceWindowSeconds;

    @Transactional
    public TradeCheckResult check(UUID tradeId) {
        TradeFact tradeFact = tradeFactRepository.findById(tradeId)
                .orElseThrow(() -> new IllegalStateException("No trade fact for tradeId: " + tradeId));

        List<LedgerFact> ledgerFacts = ledgerFactRepository.findByTradeId(tradeId);

        List<ReconciliationBreak> newBreaks = evaluate(tradeFact, ledgerFacts);

        if (newBreaks.isEmpty()) {
            List<ReconciliationBreak> openBreaks =
                    breakRepository.findByTradeIdAndStatus(tradeId, BreakStatus.OPEN);
            openBreaks.forEach(ReconciliationBreak::resolve);
            breakRepository.saveAll(openBreaks);

            tradeFact.markReconciled();
            return new TradeCheckResult(tradeId, 0, openBreaks.size());
        }

        tradeFact.markBroken();
        breakRepository.saveAll(newBreaks);
        return new TradeCheckResult(tradeId, newBreaks.size(), 0);
    }

    // Ledger-side sweep: an entry posted for a trade that has no execution record at all
    // check() cannot detect this because it is keyed on an existing TradeFact
    @Transactional
    public int checkOrphanedLedgerFacts() {
        Instant cutoff = Instant.now().minusSeconds(graceWindowSeconds);
        List<LedgerFact> orphans = ledgerFactRepository.findOrphanedBefore(cutoff);

        int raised = 0;
        for (LedgerFact orphan : orphans) {
            boolean alreadyRaised = breakRepository
                    .findByTradeIdAndStatus(orphan.getTradeId(), BreakStatus.OPEN)
                    .stream()
                    .anyMatch(b -> b.getBreakType() == BreakType.MISSING_IN_PROCESSOR);

            if (alreadyRaised) {
                continue;
            }

            breakRepository.save(ReconciliationBreak.raise(
                    orphan.getTradeId(), BreakType.MISSING_IN_PROCESSOR, BreakSeverity.CRITICAL,
                    "trade execution record present",
                    "ledger " + orphan.getEntryType() + " entry exists with no trade execution record"));
            raised++;

            log.error("Orphaned ledger entry detected. tradeId={}, journalId={}, entryType={}",
                    orphan.getTradeId(), orphan.getJournalId(), orphan.getEntryType());
        }

        // Self-heals without extra code: once the trade fact arrives it becomes PENDING,
        // check() runs, and a clean result resolves every OPEN break for that tradeId
        return raised;
    }

    private List<ReconciliationBreak> evaluate(TradeFact tradeFact, List<LedgerFact> ledgerFacts) {
        List<ReconciliationBreak> breaks = new ArrayList<>();

        Optional<LedgerFact> debit = ledgerFacts.stream()
                .filter(f -> f.getEntryType() == EntryType.DEBIT)
                .findFirst();
        Optional<LedgerFact> credit = ledgerFacts.stream()
                .filter(f -> f.getEntryType() == EntryType.CREDIT)
                .findFirst();

        long debitCount = ledgerFacts.stream().filter(f -> f.getEntryType() == EntryType.DEBIT).count();
        long creditCount = ledgerFacts.stream().filter(f -> f.getEntryType() == EntryType.CREDIT).count();

        if (ledgerFacts.isEmpty()) {
            breaks.add(ReconciliationBreak.raise(
                    tradeFact.getTradeId(), BreakType.MISSING_IN_LEDGER, BreakSeverity.CRITICAL,
                    "ledger journal present", "no ledger entries found"));
            return breaks;
        }

        if (debitCount == 0) {
            breaks.add(ReconciliationBreak.raise(
                    tradeFact.getTradeId(), BreakType.MISSING_DEBIT, BreakSeverity.CRITICAL,
                    "1 debit entry", "0 debit entries"));
        } else if (debitCount > 1) {
            breaks.add(ReconciliationBreak.raise(
                    tradeFact.getTradeId(), BreakType.DUPLICATE_LEDGER_ENTRY, BreakSeverity.CRITICAL,
                    "1 debit entry", debitCount + " debit entries"));
        }

        if (creditCount == 0) {
            breaks.add(ReconciliationBreak.raise(
                    tradeFact.getTradeId(), BreakType.MISSING_CREDIT, BreakSeverity.CRITICAL,
                    "1 credit entry", "0 credit entries"));
        } else if (creditCount > 1) {
            breaks.add(ReconciliationBreak.raise(
                    tradeFact.getTradeId(), BreakType.DUPLICATE_LEDGER_ENTRY, BreakSeverity.CRITICAL,
                    "1 credit entry", creditCount + " credit entries"));
        }

        if (debit.isPresent() && credit.isPresent()) {
            BigDecimal debitAmount = debit.get().getAmount();
            BigDecimal creditAmount = credit.get().getAmount();

            if (debitAmount.compareTo(creditAmount) != 0) {
                breaks.add(ReconciliationBreak.raise(
                        tradeFact.getTradeId(), BreakType.UNBALANCED_DOUBLE_ENTRY, BreakSeverity.CRITICAL,
                        "debit == credit", "debit=" + debitAmount + ", credit=" + creditAmount));
            }

            // Both legs are checked independently against the expected notional — checking
            // only the debit would leave a credit-side discrepancy detectable solely as a
            // side effect of UNBALANCED_DOUBLE_ENTRY, not by this check in its own right
            BigDecimal expectedAmount = tradeFact.getQuantity().multiply(tradeFact.getLimitPrice());
            boolean debitMismatch = debitAmount.compareTo(expectedAmount) != 0;
            boolean creditMismatch = creditAmount.compareTo(expectedAmount) != 0;
            if (debitMismatch || creditMismatch) {
                breaks.add(ReconciliationBreak.raise(
                        tradeFact.getTradeId(), BreakType.AMOUNT_MISMATCH, BreakSeverity.HIGH,
                        expectedAmount.toString(),
                        "debit=" + debitAmount + ", credit=" + creditAmount));
            }

            if (!debit.get().getCurrency().equals(tradeFact.getCurrency())
                    || !credit.get().getCurrency().equals(tradeFact.getCurrency())) {
                breaks.add(ReconciliationBreak.raise(
                        tradeFact.getTradeId(), BreakType.CURRENCY_MISMATCH, BreakSeverity.MEDIUM,
                        tradeFact.getCurrency(),
                        debit.get().getCurrency() + "/" + credit.get().getCurrency()));
            }
        }

        return breaks;
    }

    public record TradeCheckResult(UUID tradeId, int breaksFound, int breaksResolved) {}
}
