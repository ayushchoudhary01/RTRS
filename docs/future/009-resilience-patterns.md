# 009 — Resilience Patterns

## Current State
Services have basic Kafka retry via Spring Kafka's default error handler.
No circuit breakers, no bulkheads, no explicit timeout handling on inter-service
calls beyond the Kafka 30-second choreography timeout.

## Planned Improvements

### 1. Circuit Breakers (Resilience4j)
For any synchronous REST call between services (e.g. risk-engine → market-data):

```java
@CircuitBreaker(name = "market-data", fallbackMethod = "fallbackVaR")
public BigDecimal getVolatility(String instrumentId) { ... }

public BigDecimal fallbackVaR(String instrumentId, Exception ex) {
    // Use conservative hardcoded value when market-data is down
    return DEFAULT_VOLATILITY;
}
```

States: CLOSED → OPEN (after 5 failures) → HALF_OPEN (after 30s) → CLOSED

### 2. Bulkhead Pattern
Isolate Kafka consumer thread pools per topic so a slow consumer on one topic
doesn't starve others:

```yaml
spring.kafka.listener.concurrency: 3  # per container factory
```

Separate `ConcurrentKafkaListenerContainerFactory` beans per topic group
with independent thread pools.

### 3. Dead Letter Queue Strategy
Current: `trade.submitted.v1.DLT` topic exists but has no consumer.

Planned:
- DLQ consumer reads failed events
- Classifies: transient failure (retry) vs permanent failure (alert + manual)
- Transient: exponential backoff republish to original topic
- Permanent: persist to `failed_events` table + Slack alert to ops team

### 4. Idempotent Producer Retry
Already implemented (`enable.idempotence=true`, `retries=MAX_INT`).
Add explicit retry backoff:
```yaml
retry.backoff.ms: 100
retry.backoff.max.ms: 5000
```

### 5. Saga Compensation Completeness
Current: `ChoreographyTimeoutHandler` fires after 30s.
Improvement: graduated timeouts — warn at 10s, compensate at 30s, alert ops at 60s.


