package com.rtrs.ledgerservice.ledger.read.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class LedgerEntryResponse {
    private UUID entryId;
    private UUID journalId;
    private UUID accountId;
    private String entryType;
    private BigDecimal amount;
    private String currency;
    private BigDecimal balanceAfter;
    private String entryHash;
    private String prevHash;
    private Long sequenceNum;
    private Instant createdAt;
}
