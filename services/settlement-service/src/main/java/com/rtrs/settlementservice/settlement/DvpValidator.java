package com.rtrs.settlementservice.settlement;

import com.rtrs.settlementservice.clearing.NettedObligation;
import com.rtrs.settlementservice.instruction.SettlementInstruction;
import com.rtrs.settlementservice.instruction.SettlementInstructionRepository;
import com.rtrs.settlementservice.position.PositionBalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DvpValidator {

    private final PositionBalanceRepository positionBalanceRepository;
    private final SettlementInstructionRepository instructionRepository;
    private final RestTemplate restTemplate;

    @Value("${rtrs.ledger.base-url}")
    private String ledgerBaseUrl;

    public DvpResult validate(NettedObligation obligation, List<SettlementInstruction> instructions) {
        boolean securityAvailable = checkSecurityAvailability(obligation);
        boolean cashAvailable = checkCashAvailability(obligation, instructions);

        if (securityAvailable && cashAvailable) {
            return DvpResult.pass();
        }
        if (!securityAvailable && !cashAvailable) {
            return DvpResult.fail("Insufficient security position and insufficient cash balance");
        }
        if (!securityAvailable) {
            return DvpResult.fail("Insufficient security position for delivery");
        }
        return DvpResult.fail("Insufficient cash balance for payment");
    }

    private boolean checkSecurityAvailability(NettedObligation obligation) {
        if (!"SELL".equals(obligation.getNetDirection())) {
            return true;
        }
        return positionBalanceRepository
                .findByAccountIdAndInstrumentId(obligation.getAccountId(), obligation.getInstrumentId())
                .map(position -> position.hasSufficientQuantity(obligation.getNetQuantity()))
                .orElse(false);
    }

    private boolean checkCashAvailability(NettedObligation obligation, List<SettlementInstruction> instructions) {
        if (!"BUY".equals(obligation.getNetDirection())) {
            return true;
        }

        // Required cash = sum of notional across the actual instructions that were netted,
        // not a derived/proxy price — this is the real money that must change hands
        BigDecimal requiredCash = instructions.stream()
                .map(SettlementInstruction::notionalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        try {
            String url = ledgerBaseUrl + "/api/v1/ledger/accounts/" + obligation.getAccountId() + "/balance";
            AccountBalanceResponse balance = restTemplate.getForObject(url, AccountBalanceResponse.class);

            if (balance == null) {
                log.warn("No balance response from ledger for accountId={}", obligation.getAccountId());
                return false;
            }
            return balance.getBalance().compareTo(requiredCash) >= 0;

        } catch (Exception ex) {
            log.error("Failed to check ledger balance for accountId={}. error={}",
                    obligation.getAccountId(), ex.getMessage());
            return false;
        }
    }
}