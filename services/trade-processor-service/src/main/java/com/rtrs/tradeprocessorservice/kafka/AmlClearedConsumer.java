package com.rtrs.tradeprocessorservice.kafka;

import com.rtrs.events.aml.AmlClearedEvent;
import com.rtrs.tradeprocessorservice.choreography.TradeApprovalAggregator;
import com.rtrs.tradeprocessorservice.service.TradeExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AmlClearedConsumer {

    private final TradeApprovalAggregator approvalAggregator;
    private final TradeExecutionService tradeExecutionService;

    @KafkaListener(
            topics = "${rtrs.kafka.topics.aml-cleared}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "avroKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, AmlClearedEvent> record) {
        AmlClearedEvent event = record.value();
        UUID tradeId = UUID.fromString(event.getTradeId());
        String instrumentId = record.key();
        try {
            log.info("AML cleared event received. tradeId={}", tradeId);

            boolean bothCleared = approvalAggregator.markAmlCleared(tradeId);
            if (bothCleared) {
                tradeExecutionService.execute(tradeId, instrumentId);
            }
        } catch (IllegalStateException ex) {
            log.warn("Approval state not found for tradeId={}, will retry.", tradeId);
            throw new RuntimeException(ex);
        } catch (Exception ex) {
            log.error("Failed to process aml.cleared event. error={}", ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }
}
