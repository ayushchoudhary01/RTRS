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
        // FLAT — buys and sells cancelled out exactly, so no delivery leg and no payment leg
        if ("FLAT".equals(obligation.getNetDirection())) {
            log.info("Obligation nets to zero, nothing to settle. accountId={}, instrumentId={}",
                    obligation.getAccountId(), obligation.getInstrumentId());
            return DvpResult.pass();
        }

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

        // Required cash = NET notional across the netted instructions — BUY legs cost cash,
        // SELL legs bring it in; summing gross notional across both directions would demand
        // more cash than a netted obligation actually needs, making netting reduce the
        // quantity obligation while leaving the cash obligation un-netted
        BigDecimal requiredCash = instructions.stream()
                .map(si -> "BUY".equals(si.getTradeType())
                        ? si.notionalAmount()
                        : si.notionalAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (requiredCash.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }

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