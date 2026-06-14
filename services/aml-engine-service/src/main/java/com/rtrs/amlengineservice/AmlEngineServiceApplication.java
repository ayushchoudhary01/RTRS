package com.rtrs.amlengineservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class AmlEngineServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AmlEngineServiceApplication.class, args);
    }

}
