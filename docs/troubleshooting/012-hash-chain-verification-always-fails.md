# 012 — Hash Chain Verification Always Fails Despite an Unbroken Chain

## Symptom
`verifyChainIntegrity()` returns `false` for **every** account, including accounts
that had not been written to in months:

```
GET /api/v1/ledger/accounts/550e8460-.../integrity
{"chainIntact":false,"accountId":"550e8460-...","verifiedAt":"..."}
```

`GET /api/v1/admin/ledger/integrity/verify-all` returned `chainIntact: false` for
all 23 accounts. `ChainIntegrityScheduler` had therefore been logging a tamper
alert every night since the ledger was built.

The confusing part: the chain itself was provably fine. Every entry's `prevHash`
equalled the previous entry's `entryHash`, with no gaps, so nothing had been
tampered with. Only the *recomputation* disagreed.

## Root Cause
`verifyChain()` re-hashes each entry from its persisted fields and compares the
result against the stored `entryHash`. One of those fields is `createdAt`.

`LedgerService` was writing:

```java
Instant debitTime = Instant.now();
```

On Windows with JDK 21, `Instant.now()` carries sub-microsecond precision
(100-nanosecond ticks). PostgreSQL `TIMESTAMPTZ` stores **microseconds** and
**rounds** anything finer.

So the value fed into SHA-256 at write time and the value read back at verify
time were different strings:

```
hashed:    2026-06-21T13:31:51.230627500Z
persisted: 2026-06-21T13:31:51.230628Z
```

A one-digit difference changes the digest completely, so verification could never
succeed. This was confirmed by brute-forcing the lost precision — re-hashing the
stored entry across ±1 microsecond x 1000 nanoseconds found the exact original
value above.

Confirmation that Postgres is the one dropping the precision:

```sql
SELECT '2026-10-11T12:34:56.123456Z'::timestamptz,   -- 12:34:56.123456  kept
       '2026-10-11T12:34:56.1234565Z'::timestamptz;  -- 12:34:56.123456  altered
```

## Fix
Truncate the instant to the precision the database can actually store, so that
the hashed value and the persisted value are identical by construction:

```java
Instant debitTime = Instant.now().truncatedTo(ChronoUnit.MICROS);
...
Instant creditTime = Instant.now().truncatedTo(ChronoUnit.MICROS);
```

`LedgerService.recordTrade()` is the only writer of `ledger_entries`, and the same
`Instant` instance flows into both `computeHash()` and `LedgerEntry.create()`, so
one truncation at the source covers both.

## Result
A trade written after the fix verifies, and its timestamps now carry exactly six
fractional digits:

```
"createdAt":"2026-10-11T05:46:27.152812Z"
{"chainIntact":true,"accountId":"9e1c7a40-...","verifiedAt":"..."}
```

Recomputing the genesis entry's digest independently of the service confirms the
check is doing real work rather than trivially passing:

```
printf '%s' "<prevHash><entryId><accountId>DEBIT1000.00000000002026-10-11T05:46:27.152812Z" | sha256sum
ab18f2a87cef03cbfb7ed8ce75bfdfeefa705cb5bdf7225ccbde33e64fc043bf   computed
ab18f2a87cef03cbfb7ed8ce75bfdfeefa705cb5bdf7225ccbde33e64fc043bf   stored
```

**Entries written before the fix stay unverifiable forever.** Their original
sub-microsecond digits were never stored, so the input to the hash cannot be
reconstructed. `verifyChain()` walks every entry for an account, so any account
holding even one pre-fix entry keeps returning `false`. Demonstrating a passing
chain requires a fresh account.

## Why It Went Unnoticed For So Long
The operations console reported the chain as healthy. It read the response as
`verdict.valid ?? verdict.intact`, but the endpoint returns `chainIntact`. Both
lookups were `undefined`, and the guard was written as `chainOk === false` — which
`undefined` fails, so the UI took the success branch and rendered a green tick over
a failing check.

Corrected to require the field explicitly:

```ts
const chainOk = verdict?.chainIntact === true
```

A boolean crossing an API boundary should be tested for the value that permits the
safe branch, never for the value that forbids it. Written the other way, a renamed
or missing field silently becomes a pass.

## Lesson
When a value is both hashed and persisted, the hash must be computed over the
value **at storage precision**. Any field whose round-trip through the database is
lossy — timestamps, scaled decimals, text collation — will break a digest that was
computed before the round-trip, and it will break it 100% of the time rather than
intermittently, which is what made this look like a logic error rather than a
precision one.

## Affected
- `LedgerService.recordTrade()` in `ledger-service`
- `HashChainService.verifyChain()` (correct as written, but could never pass)
- `ChainIntegrityScheduler` (false nightly tamper alerts)
- `LedgerPage.tsx` in the frontend (false green)
