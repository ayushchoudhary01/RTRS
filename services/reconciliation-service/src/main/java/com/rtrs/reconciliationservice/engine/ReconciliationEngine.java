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

            BigDecimal expectedAmount = tradeFact.getQuantity().multiply(tradeFact.getLimitPrice());
            if (debitAmount.compareTo(expectedAmount) != 0) {
                breaks.add(ReconciliationBreak.raise(
                        tradeFact.getTradeId(), BreakType.AMOUNT_MISMATCH, BreakSeverity.HIGH,
                        expectedAmount.toString(), debitAmount.toString()));
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
