# 002 — Time Travel Queries and Position Snapshots

## Current State
`AccountBalance` is maintained as a live projection updated atomically with each
ledger entry. Current balance queries are O(1). Historical balance requires
replaying all entries from the beginning — O(n) where n is entry count.

## Planned Improvement

### PositionSnapshotService
Every 1000 entries per account, take a snapshot:
```
LedgerSnapshot {
    accountId
    balance
    snapshotAt
    eventSequence  ← entry number at snapshot time
}
```

### TimeTravelQueryService
Balance at time T:
1. Find the nearest snapshot before T
2. Replay only the delta entries from snapshot to T
3. Return the computed balance

```
Balance at T = snapshot.balance + sum(entries from snapshot.eventSequence to T)
```

## Benefits
- Historical balance queries become O(delta) instead of O(total entries)
- Critical for regulatory reporting — "what was this account's balance on date X"
- Required for end-of-month reporting, tax calculations, audit trails

## Implementation Notes
- `LedgerSnapshot` table needed: `id, account_id, balance, snapshot_at, event_sequence`
- Snapshot trigger: scheduled job runs after every 1000 entries per account
- `TimeTravelQueryService.getBalanceAt(accountId, Instant)` is the core API
- New endpoint: `GET /api/v1/ledger/accounts/{accountId}/balance?at=2025-01-01T00:00:00Z`



