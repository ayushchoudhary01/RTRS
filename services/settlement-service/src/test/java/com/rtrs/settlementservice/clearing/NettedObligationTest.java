package com.rtrs.settlementservice.clearing;

import com.rtrs.settlementservice.instruction.SettlementInstruction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NettedObligationTest {

    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID RUN = UUID.randomUUID();
    private static final String INSTRUMENT = "AAPL";
    private static final LocalDate SETTLES = LocalDate.of(2026, 10, 13);

    private SettlementInstruction instruction(String side, String quantity) {
        return SettlementInstruction.create(
                UUID.randomUUID(), ACCOUNT, INSTRUMENT, side,
                new BigDecimal(quantity), new BigDecimal("100.00"), "USD", SETTLES);
    }

    private NettedObligation net(List<SettlementInstruction> instructions) {
        return NettedObligation.net(ACCOUNT, INSTRUMENT, SETTLES, instructions, RUN);
    }

    @Test
    @DisplayName("BUY 100 + BUY 40 + SELL 20 nets to a single BUY 120")
    void buysAndSellsCollapseToOneObligation() {
        NettedObligation obligation = net(List.of(
                instruction("BUY", "100"),
                instruction("BUY", "40"),
                instruction("SELL", "20")));

        assertThat(obligation.getNetDirection()).isEqualTo("BUY");
        assertThat(obligation.getNetQuantity()).isEqualByComparingTo("120");
        assertThat(obligation.getInstructionCount()).isEqualTo(3);
        assertThat(obligation.getSettlementRunId()).isEqualTo(RUN);
    }

    @Test
    @DisplayName("a sell heavy group nets to SELL with a positive quantity")
    void sellHeavyGroupNetsToSell() {
        NettedObligation obligation = net(List.of(
                instruction("BUY", "10"),
                instruction("SELL", "60")));

        assertThat(obligation.getNetDirection()).isEqualTo("SELL");
        // Quantity is the size of the obligation, direction carries the sign
        assertThat(obligation.getNetQuantity()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("buys and sells that cancel exactly are FLAT, not a zero quantity BUY")
    void exactlyOffsettingGroupIsFlat() {
        NettedObligation obligation = net(List.of(
                instruction("BUY", "50"),
                instruction("SELL", "50")));

        assertThat(obligation.getNetDirection()).isEqualTo("FLAT");
        assertThat(obligation.getNetQuantity()).isEqualByComparingTo("0");
        assertThat(obligation.getInstructionCount()).isEqualTo(2);
    }
}
