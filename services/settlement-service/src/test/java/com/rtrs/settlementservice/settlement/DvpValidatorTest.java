package com.rtrs.settlementservice.settlement;

import com.rtrs.settlementservice.clearing.NettedObligation;
import com.rtrs.settlementservice.instruction.SettlementInstruction;
import com.rtrs.settlementservice.instruction.SettlementInstructionRepository;
import com.rtrs.settlementservice.position.PositionBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DvpValidatorTest {

    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final String INSTRUMENT = "AAPL";
    private static final LocalDate SETTLES = LocalDate.of(2026, 10, 13);

    @Mock private PositionBalanceRepository positionBalanceRepository;
    @Mock private SettlementInstructionRepository instructionRepository;
    @Mock private RestTemplate restTemplate;

    private DvpValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DvpValidator(positionBalanceRepository, instructionRepository, restTemplate);
        ReflectionTestUtils.setField(validator, "ledgerBaseUrl", "http://ledger:8085");
    }

    private SettlementInstruction instruction(String side, String quantity, String price) {
        return SettlementInstruction.create(
                UUID.randomUUID(), ACCOUNT, INSTRUMENT, side,
                new BigDecimal(quantity), new BigDecimal(price), "USD", SETTLES);
    }

    private NettedObligation obligationFor(List<SettlementInstruction> instructions) {
        return NettedObligation.net(ACCOUNT, INSTRUMENT, SETTLES, instructions, UUID.randomUUID());
    }

    private void ledgerReturnsBalance(String balance) {
        AccountBalanceResponse response = new AccountBalanceResponse();
        response.setAccountId(ACCOUNT);
        response.setBalance(new BigDecimal(balance));
        response.setCurrency("USD");
        when(restTemplate.getForObject(anyString(), any())).thenReturn(response);
    }

    @Test
    @DisplayName("a FLAT obligation settles without checking cash or securities")
    void flatObligationNeedsNoChecks() {
        List<SettlementInstruction> instructions = List.of(
                instruction("BUY", "50", "100.00"),
                instruction("SELL", "50", "100.00"));

        DvpResult result = validator.validate(obligationFor(instructions), instructions);

        assertThat(result.isPassed()).isTrue();
        // Nothing to deliver and nothing to pay, so neither leg is consulted
        verifyNoInteractions(restTemplate, positionBalanceRepository);
    }

    @Test
    @DisplayName("a BUY needs only the NET cash of the obligation, not the gross of every leg")
    void buyRequiresNetCashNotGross() {
        // BUY 100 @ 100 costs 10,000 and SELL 40 @ 100 brings in 4,000 — net 6,000
        List<SettlementInstruction> instructions = List.of(
                instruction("BUY", "100", "100.00"),
                instruction("SELL", "40", "100.00"));
        NettedObligation obligation = obligationFor(instructions);
        ledgerReturnsBalance("6000.00");

        DvpResult result = validator.validate(obligation, instructions);

        assertThat(obligation.getNetDirection()).isEqualTo("BUY");
        assertThat(result.isPassed())
                .as("6,000 covers the net obligation, though it would not cover the 10,000 gross")
                .isTrue();
    }

    @Test
    @DisplayName("a BUY short of the net cash fails the payment leg")
    void insufficientCashFailsThePaymentLeg() {
        List<SettlementInstruction> instructions = List.of(instruction("BUY", "100", "100.00"));
        ledgerReturnsBalance("9999.99");

        DvpResult result = validator.validate(obligationFor(instructions), instructions);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo("Insufficient cash balance for payment");
    }

    @Test
    @DisplayName("a SELL with no recorded position cannot deliver")
    void sellWithoutPositionFailsTheDeliveryLeg() {
        List<SettlementInstruction> instructions = List.of(instruction("SELL", "25", "100.00"));
        NettedObligation obligation = obligationFor(instructions);
        when(positionBalanceRepository.findByAccountIdAndInstrumentId(ACCOUNT, INSTRUMENT))
                .thenReturn(Optional.empty());

        DvpResult result = validator.validate(obligation, instructions);

        assertThat(obligation.getNetDirection()).isEqualTo("SELL");
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo("Insufficient security position for delivery");
    }
}
