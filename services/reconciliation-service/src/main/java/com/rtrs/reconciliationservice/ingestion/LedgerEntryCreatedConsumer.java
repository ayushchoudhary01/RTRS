package com.rtrs.reconciliationservice.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtrs.reconciliationservice.domain.LedgerFact;
import com.rtrs.reconciliationservice.enums.EntryType;
import com.rtrs.reconciliationservice.repository.LedgerFactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerEntryCreatedConsumer {

    private final LedgerFactRepository ledgerFactRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${rtrs.kafka.topics.ledger-entry-created}",
            groupId = "reconciliation-group"
    )
    public void consume(ConsumerRecord<String, String> record) {
        log.info("Ledger entry created event received for reconciliation. offset={}, key={}, partition={}",
                record.offset(), record.key(), record.partition());
        try {
            JsonNode payload = objectMapper.readTree(record.value());

            UUID journalId = UUID.fromString(payload.get("journalId").asText());
            String entryTypeRaw = payload.get("entryType").asText();
            EntryType entryType = EntryType.valueOf(entryTypeRaw);

            if (ledgerFactRepository.existsByJournalIdAndEntryType(journalId, entryType)) {
                log.info("Ledger fact already recorded, skipping. journalId={}, entryType={}",
                        journalId, entryType);
                return;
            }

            UUID tradeId = UUID.fromString(payload.get("tradeId").asText());
            BigDecimal amount = new BigDecimal(payload.get("amount").asText());
            String currency = payload.get("currency").asText();
            Instant ledgerCreatedAt = Instant.parse(payload.get("createdAt").asText());

            LedgerFact fact = LedgerFact.create(
                    tradeId, journalId, entryType, amount, currency, ledgerCreatedAt);

            ledgerFactRepository.save(fact);
            log.info("Ledger fact recorded. tradeId={}, journalId={}, entryType={}",
                    tradeId, journalId, entryType);

        } catch (Exception ex) {
            log.error("Failed to process ledger.entry.created event for reconciliation. offset={}, error={}",
                    record.offset(), ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }
}
