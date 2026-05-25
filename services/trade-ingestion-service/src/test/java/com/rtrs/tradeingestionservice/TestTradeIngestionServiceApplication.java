package com.rtrs.tradeingestionservice;

import org.springframework.boot.SpringApplication;

public class TestTradeIngestionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(TradeIngestionServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
