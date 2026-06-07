package com.rtrs.tradeprocessorservice.statemachine;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachine;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

@Configuration
@EnableStateMachine
@RequiredArgsConstructor
public class TradeStateMachineConfig extends StateMachineConfigurerAdapter<TradeApprovalStatus, TradeEvent> {

    @Override
    public void configure(StateMachineStateConfigurer<TradeApprovalStatus, TradeEvent> states) throws Exception {
        states.withStates()
                .initial(TradeApprovalStatus.SUBMITTED)
                .state(TradeApprovalStatus.RISK_CLEARED)
                .state(TradeApprovalStatus.AML_CLEARED)
                .state(TradeApprovalStatus.EXECUTING)
                .end(TradeApprovalStatus.EXECUTED)
                .end(TradeApprovalStatus.REJECTED);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<TradeApprovalStatus, TradeEvent> transitions) throws Exception {
        transitions
                .withExternal()
                .source(TradeApprovalStatus.SUBMITTED).target(TradeApprovalStatus.RISK_CLEARED)
                .event(TradeEvent.RISK_APPROVED).and()
                .withExternal()
                .source(TradeApprovalStatus.SUBMITTED).target(TradeApprovalStatus.REJECTED)
                .event(TradeEvent.RISK_REJECTED).and()
                .withExternal()
                .source(TradeApprovalStatus.RISK_CLEARED).target(TradeApprovalStatus.EXECUTING)
                .event(TradeEvent.AML_CLEARED).and()
                .withExternal()
                .source(TradeApprovalStatus.SUBMITTED).target(TradeApprovalStatus.AML_CLEARED)
                .event(TradeEvent.AML_CLEARED).and()
                .withExternal()
                .source(TradeApprovalStatus.AML_CLEARED).target(TradeApprovalStatus.EXECUTING)
                .event(TradeEvent.RISK_APPROVED).and()
                .withExternal()
                .source(TradeApprovalStatus.EXECUTING).target(TradeApprovalStatus.EXECUTED)
                .event(TradeEvent.EXECUTE).and()
                .withExternal()
                .source(TradeApprovalStatus.RISK_CLEARED).target(TradeApprovalStatus.REJECTED)
                .event(TradeEvent.AML_FLAGGED).and()
                .withExternal()
                .source(TradeApprovalStatus.AML_CLEARED).target(TradeApprovalStatus.REJECTED)
                .event(TradeEvent.RISK_REJECTED).and()
                .withExternal()
                .source(TradeApprovalStatus.SUBMITTED).target(TradeApprovalStatus.REJECTED)
                .event(TradeEvent.TIMEOUT);
    }
}