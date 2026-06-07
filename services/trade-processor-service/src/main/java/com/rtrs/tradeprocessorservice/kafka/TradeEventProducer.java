package com.rtrs.tradeprocessorservice.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class TradeEventProducer {

    private final KafkaTemplate<String,String> kafkaTemplate;

    // Direct Kafka publish — sirf OutboxPublisher use karta hai isko
    // Controller ya service directly nhi call krta — outbox pattern ke through aata hai
    public CompletableFuture<SendResult<String, String>> publish(String topic, String partitionKey, String payload) {
        return kafkaTemplate.send(topic, partitionKey, payload);
    }
}
