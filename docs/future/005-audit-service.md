# 005 — Audit Service

## Current State
No dedicated audit trail exists across the system. Two services already
demonstrate immutability patterns independently:
- `ledger-service` — append-only `ledger_entries` table with a PostgreSQL
  trigger blocking UPDATE/DELETE, plus a SHA-256 hash chain per account
- `reconciliation-service` — `reconciliation_runs` table giving a full history
  of every reconciliation pass with timestamps and outcomes

There is no single place that captures every domain event across every
service for compliance/forensic purposes.

## Planned Improvement
A stateless-ish consumer service that subscribes to every domain event topic
and writes an immutable record of what happened, when, and in what order —
independent of any individual service's own state.

```
trade.submitted.v1 ─┐
trade.executed.v1   ├──→ audit-service ──→ audit_log (append-only)
trade.rejected.v1    │
risk.approved.v1     │
risk.breached.v1     │
aml.cleared.v1       │
aml.flagged.v1      ─┘
```

### Schema
```sql
CREATE TABLE audit_log
(
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type  VARCHAR(100) NOT NULL,
    trade_id    UUID,
    source_topic VARCHAR(255) NOT NULL,
    raw_payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```
Same append-only trigger pattern as `ledger_entries` — no UPDATE, no DELETE,
ever, enforced at the DB level.

### Why It's Genuinely Lower Priority
This service would mostly repeat the append-only enforcement pattern already
demonstrated twice (ledger's trigger, reconciliation's run history) rather
than introduce a new technical idea. The marginal engineering lesson it
teaches beyond what's already built is small. It's valuable as a completeness
item for a real production system, but not as a differentiator in a
portfolio project that already proves the append-only pattern works.


