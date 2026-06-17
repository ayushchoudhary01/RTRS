package com.rtrs.ledgerservice.ledger.write;

import com.rtrs.ledgerservice.ledger.domain.LedgerEntry;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class HashChainService {

    private static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    // SHA-256(prevHash + entryId + accountId + entryType + amount + createdAt)
    public String computeHash(String prevHash, UUID entryId, UUID accountId,
                              String entryType, BigDecimal amount, Instant createdAt) {
        try {
            String input = prevHash
                    + entryId.toString()
                    + accountId.toString()
                    + entryType
                    + amount.toPlainString()
                    + createdAt.toString();

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();

        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("SHA-256 not available", ex);
        }
    }

    public String getGenesisHash() {
        return GENESIS_HASH;
    }

    // Chain integrity verification — re-computes and compares
    public boolean verifyChain(List<LedgerEntry> entries) {
        if (entries.isEmpty()) return true;

        String expectedPrevHash = GENESIS_HASH;
        for (LedgerEntry entry : entries) {
            if (!entry.getPrevHash().equals(expectedPrevHash)) {
                return false;
            }
            String computedHash = computeHash(
                    entry.getPrevHash(),
                    entry.getId(),
                    entry.getAccountId(),
                    entry.getEntryType(),
                    entry.getAmount(),
                    entry.getCreatedAt()
            );
            if (!computedHash.equals(entry.getEntryHash())) {
                return false;
            }
            expectedPrevHash = entry.getEntryHash();
        }
        return true;
    }
}