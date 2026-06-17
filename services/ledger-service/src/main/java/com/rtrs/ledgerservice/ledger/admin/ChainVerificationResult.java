package com.rtrs.ledgerservice.ledger.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class ChainVerificationResult {
    private UUID accountId;
    private boolean chainIntact;
    private long entryCount;
    private String lastEntryHash;
    private Instant verifiedAt;
    private String violationDetail;
}
