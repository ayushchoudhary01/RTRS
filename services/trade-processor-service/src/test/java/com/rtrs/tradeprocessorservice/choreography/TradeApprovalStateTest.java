package com.rtrs.tradeprocessorservice.choreography;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TradeApprovalStateTest {

    private TradeApprovalState newState() {
        return TradeApprovalState.create(
                UUID.randomUUID(), "AAPL", UUID.randomUUID(),
                new BigDecimal("10"), new BigDecimal("100.00"), "USD");
    }

    @Test
    @DisplayName("risk alone does not approve the trade")
    void riskAloneIsNotApproval() {
        TradeApprovalState state = newState();
        state.markRiskCleared();

        assertThat(state.getStatus()).isEqualTo("RISK_CLEARED");
        assertThat(state.isBothCleared()).isFalse();
        assertThat(state.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("aml alone does not approve the trade")
    void amlAloneIsNotApproval() {
        TradeApprovalState state = newState();
        state.markAmlCleared();

        assertThat(state.getStatus()).isEqualTo("AML_CLEARED");
        assertThat(state.isBothCleared()).isFalse();
        assertThat(state.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("either arrival order approves — the two verdicts are independent")
    void clearanceOrderDoesNotMatter() {
        TradeApprovalState riskFirst = newState();
        riskFirst.markRiskCleared();
        riskFirst.markAmlCleared();

        TradeApprovalState amlFirst = newState();
        amlFirst.markAmlCleared();
        amlFirst.markRiskCleared();

        assertThat(riskFirst.getStatus()).isEqualTo("APPROVED");
        assertThat(amlFirst.getStatus()).isEqualTo("APPROVED");
        assertThat(riskFirst.isTerminal()).isTrue();
        assertThat(riskFirst.isRejected()).isFalse();
    }

    @Test
    @DisplayName("a verdict arriving after rejection must not resurrect the trade")
    void lateVerdictCannotOverrideRejection() {
        TradeApprovalState state = newState();
        state.markRiskCleared();
        state.markRejected("Risk limit breached");

        // The AML verdict was in flight when the rejection landed
        state.markAmlCleared();

        assertThat(state.getStatus()).isEqualTo("REJECTED");
        assertThat(state.isAmlCleared()).isFalse();
        assertThat(state.isRejected()).isTrue();
    }

    @Test
    @DisplayName("a verdict arriving after the 30 second timeout must not resurrect the trade")
    void lateVerdictCannotOverrideTimeout() {
        TradeApprovalState state = newState();
        state.markTimeout();

        state.markRiskCleared();
        state.markAmlCleared();

        assertThat(state.getStatus()).isEqualTo("TIMEOUT");
        assertThat(state.isBothCleared()).isFalse();
        assertThat(state.isRejected()).isTrue();
        assertThat(state.getRejectionReason()).contains("30 seconds");
    }
}
