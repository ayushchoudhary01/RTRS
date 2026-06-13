# 005 — Outbox Publisher Sending Duplicate Events to Kafka

## Symptom
Same trade event consumed twice by `trade-processor-service`:
```
Trade submitted event received. offset=2, key=AAPL, partition=0
Approval state initiated. tradeId=7fcdd4b1-bdde-4466-94c8-c84c2ede6e3d

Trade submitted event received. offset=3, key=AAPL, partition=0
Approval state already exists for tradeId=7fcdd4b1-bdde-4466-94c8-c84c2ede6e3d
```
Two different Kafka offsets for the same `tradeId`. Idempotency guard in
`TradeApprovalAggregator` blocked the second processing, but the duplicate publish
itself was the bug.

## Root Cause
`OutboxPublisher.publishEvent()` used `kafkaTemplate.send(...).whenComplete(...)`.
The `whenComplete` callback fires on a separate thread after the Kafka network
response — which is after the `@Transactional` method on the scheduler thread has
already returned. The sequence was:

```
Poll → find event (processed=false) → publish async → method returns → transaction commits
                                                     ↓ (100ms later)
                                               Next poll → finds same event (processed=false)
                                                     ↓ (async later)
                                         whenComplete fires → markProcessed() → save
```

The next poll fired before `whenComplete` had a chance to mark the event as processed,
so the same event was picked up and published again.

## Solution
Replace the async `whenComplete` callback with a synchronous `.get()` call.
This blocks the scheduler thread until Kafka acknowledges the send, ensuring
`markProcessed()` is called and saved before the method returns and the next
poll can fire.

```java
// BEFORE — async, race condition
tradeEventProducer.publish(topic, key, payload)
    .whenComplete((result, ex) -> {
        event.markProcessed();
        outboxEventRepository.save(event);
    });

// AFTER — synchronous, correct
tradeEventProducer.publish(topic, key, payload).get();
event.markProcessed();
outboxEventRepository.save(event);
```

## Affected Services
Both `trade-ingestion-service` and `trade-processor-service` had the same bug —
same `OutboxPublisher` pattern copy-pasted. Fix applied to both.

## Verified
After fix: single event per trade, single offset, no duplicate warn logs.
```
Trade submitted event received. offset=4, key=AAPL, partition=0
Approval state initiated. tradeId=5a036bdc-1878-4510-88a7-4ab5ee75eb5c
```