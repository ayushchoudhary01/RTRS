package com.rtrs.ledgerservice.ledger.write;

import com.rtrs.ledgerservice.ledger.domain.LedgerEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HashChainServiceTest {

    private static final UUID ACCOUNT = UUID.fromString("9e1c7a40-3b55-4d2e-8f61-0a7b5c2d4e10");
    private static final String GENESIS = "0".repeat(64);

    private final HashChainService hashChain = new HashChainService();

    // Builds an entry the way LedgerService does — hash computed over the same
    // instant that is stored on the entry
    private LedgerEntry entry(String prevHash, String type, String amount, long seq, Instant at) {
        UUID id = UUID.randomUUID();
        BigDecimal value = new BigDecimal(amount);
        String hash = hashChain.computeHash(prevHash, id, ACCOUNT, type, value, at);
        return LedgerEntry.create(id, UUID.randomUUID(), ACCOUNT, type, value, "USD",
                BigDecimal.ZERO, prevHash, hash, seq, at);
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @Test
    @DisplayName("a well formed chain of four entries verifies")
    void wellFormedChainVerifies() {
        LedgerEntry a = entry(GENESIS, "DEBIT", "1000.0000000000", 0, now());
        LedgerEntry b = entry(a.getEntryHash(), "CREDIT", "1000.0000000000", 1, now());
        LedgerEntry c = entry(b.getEntryHash(), "DEBIT", "250.5000000000", 2, now());
        LedgerEntry d = entry(c.getEntryHash(), "CREDIT", "250.5000000000", 3, now());

        assertThat(hashChain.verifyChain(List.of(a, b, c, d))).isTrue();
    }

    @Test
    @DisplayName("altering an amount after the fact is detected")
    void tamperedAmountIsDetected() {
        Instant at = now();
        LedgerEntry original = entry(GENESIS, "DEBIT", "1000.0000000000", 0, at);

        // Same id, same position in the chain, same stored hash — only the amount moved
        LedgerEntry tampered = LedgerEntry.create(
                original.getId(), UUID.randomUUID(), ACCOUNT, "DEBIT",
                new BigDecimal("9999.0000000000"), "USD", BigDecimal.ZERO,
                GENESIS, original.getEntryHash(), 0L, at);

        assertThat(hashChain.verifyChain(List.of(tampered))).isFalse();
    }

    @Test
    @DisplayName("removing an entry from the middle breaks the linkage")
    void brokenLinkageIsDetected() {
        LedgerEntry a = entry(GENESIS, "DEBIT", "1000.0000000000", 0, now());
        LedgerEntry b = entry(a.getEntryHash(), "CREDIT", "1000.0000000000", 1, now());
        LedgerEntry c = entry(b.getEntryHash(), "DEBIT", "500.0000000000", 2, now());

        // Each entry is individually valid, but c points at a hash no longer present
        assertThat(hashChain.verifyChain(List.of(a, c))).isFalse();
    }

    @Test
    @DisplayName("regression: a sub-microsecond timestamp cannot be re-verified once stored")
    void subMicrosecondTimestampBreaksVerification() {
        // Instant.now() carries 100ns ticks, Postgres TIMESTAMPTZ rounds to micros
        // these are the exact values observed when this bug was found
        Instant hashedAt = Instant.parse("2026-06-21T13:31:51.230627500Z");
        Instant storedAt = Instant.parse("2026-06-21T13:31:51.230628Z");

        UUID id = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("1000.0000000000");
        String hash = hashChain.computeHash(GENESIS, id, ACCOUNT, "DEBIT", amount, hashedAt);

        LedgerEntry asReadBack = LedgerEntry.create(id, UUID.randomUUID(), ACCOUNT, "DEBIT",
                amount, "USD", BigDecimal.ZERO, GENESIS, hash, 0L, storedAt);

        assertThat(hashChain.verifyChain(List.of(asReadBack)))
                .as("hashing a precision the database cannot store makes the chain unverifiable")
                .isFalse();
    }

    @Test
    @DisplayName("truncating to microseconds before hashing survives the round trip")
    void microsecondTruncationSurvivesStorage() {
        Instant writeTime = Instant.parse("2026-06-21T13:31:51.230627500Z")
                .truncatedTo(ChronoUnit.MICROS);

        // What Postgres gives back is identical, because there is nothing left to round
        assertThat(writeTime).isEqualTo(writeTime.truncatedTo(ChronoUnit.MICROS));
        assertThat(hashChain.verifyChain(List.of(entry(GENESIS, "DEBIT", "1000.0000000000", 0, writeTime))))
                .isTrue();
    }
}
