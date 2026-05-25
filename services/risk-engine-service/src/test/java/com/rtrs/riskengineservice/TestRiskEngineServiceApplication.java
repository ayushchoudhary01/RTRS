package com.rtrs.riskengineservice;

import org.springframework.boot.SpringApplication;

public class TestRiskEngineServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(RiskEngineServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
