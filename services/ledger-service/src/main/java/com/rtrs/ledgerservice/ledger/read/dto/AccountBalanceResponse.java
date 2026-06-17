package com.rtrs.ledgerservice.ledger.read.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class AccountBalanceResponse {
    private UUID accountId;
    private BigDecimal balance;
    private String currency;
    private Long entryCount;
    private String lastEntryHash;
    private Instant lastEntryAt;
    private Instant updatedAt;
}
