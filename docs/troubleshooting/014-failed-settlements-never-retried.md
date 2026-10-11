# 014 — Failed Settlements Entered `RETRYING` And Were Never Picked Up Again

## Symptom
A settlement instruction whose DVP check failed moved to `FAILED`, then
`RETRYING`, and then stopped. It never reached `SETTLED`, never reached
`ESCALATED`, and never incremented `retryCount` past 1. Every subsequent
settlement run reported it as neither processed nor failed — it was simply absent
from the run.

The lifecycle documented in `docs/architecture/06-settlement-service.md` —
`FAILED → RETRYING → FAILED → ESCALATED` — could not actually complete.

## Root Cause
`SettlementLifecycleService.confirmDueInstructions()` selected the work for a run
through a repository method that matched a **single** status:

```java
findByStatusAndSettlementDateLessThanEqual(SettlementStatus.PENDING, today)
```

An instruction in `RETRYING` is by definition no longer `PENDING`, so it could
never match the selection query again. `FailedSettlementHandler` was setting the
status to `RETRYING` correctly, and `max-retry-attempts` was configured correctly;
the retry simply had no mechanism to re-select the row.

The `RETRYING` state existed to remove the ambiguity between "currently retrying"
and "given up entirely", and the state transition into it worked. Nothing read it
back out.

## Fix
Select every status that is eligible for a settlement attempt, not just the
initial one:

```java
private static final List<SettlementStatus> SETTLEABLE_STATUSES =
        List.of(SettlementStatus.PENDING, SettlementStatus.RETRYING);
...
instructionRepository.findByStatusInAndSettlementDateLessThanEqual(SETTLEABLE_STATUSES, today);
```

The repository method was changed from `findByStatus...` to
`findByStatusIn...`. The old single-status method had no remaining callers and was
deleted rather than left behind.

## Result
Verified against running services. A failing instruction now cycles properly and
terminates at the configured limit:

```
status       ESCALATED
retryCount   3
failureReason  Insufficient cash balance for payment
```

and successive `SettlementRun` rows show the instruction being re-selected on each
run rather than disappearing after the first failure.

## Lesson
The settlement lifecycle is a plain `status` column, not a framework — the
transitions are ordinary writes, and the only thing that advances an instruction is
a query selecting it. So when a lifecycle gains an intermediate status, every query
that drives it has to be revisited alongside the transitions. Writing *into* a
status is half the implementation; something has to select *on* it.

This is easy to miss because the write side looks finished and the column visibly
holds the right value. The row is correct. It is just orphaned.

## Affected
- `SettlementLifecycleService.confirmDueInstructions()`
- `SettlementInstructionRepository`
