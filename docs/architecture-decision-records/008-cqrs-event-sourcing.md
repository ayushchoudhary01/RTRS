# 008 — CQRS and Event Sourcing in Ledger Service

## Status
Accepted

## Decision
The Ledger Service uses CQRS (Command Query Responsibility Segregation) and Event Sourcing. The write model is an append-only event store. The read model is a projection rebuilt from events.

## Context
A financial ledger has strict requirements: every entry must be immutable, auditable, and reproducible at any point in time. Traditional CRUD approaches (UPDATE balance SET amount = X) destroy history.

## Reasons
- **Immutability** — ledger entries are never updated or deleted. Corrections create new entries. This is legally required for financial audit trails.
- **Time-travel queries** — because every state change is recorded as an event, we can replay events up to any timestamp T to reconstruct the exact balance at that moment.
- **Audit compliance** — the event store is the complete history of every financial transaction. No separate audit log needed.
- **CQRS** — separating writes (event store) from reads (projections) allows each to be optimised independently. The read model is a denormalised projection optimised for query performance.
- **Hash chain integrity** — each audit entry stores `SHA-256(previousHash + payload)`. Tampering with any entry breaks the chain, which is detectable by a full scan.

## Alternatives Rejected
- **Traditional CRUD** — destroys history, no time-travel, not audit-compliant.
- **Separate audit table** — redundant, can get out of sync with the main table.

## Consequences
- Read model may be slightly behind the write model (eventual consistency).
- Event replay for time-travel queries can be slow for old accounts — mitigated by periodic snapshots.
- No UPDATE or DELETE statements ever executed on the ledger event store.
