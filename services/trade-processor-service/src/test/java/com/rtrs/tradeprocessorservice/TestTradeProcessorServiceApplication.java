package com.rtrs.tradeprocessorservice;

import org.springframework.boot.SpringApplication;

public class TestTradeProcessorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(TradeProcessorServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
