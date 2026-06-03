# 010 — Kafka Partition Key: instrumentId for Trade Ordering

## Status
Accepted

## Decision
Trade-related Kafka topics use `instrumentId` as the partition key, guaranteeing strict ordering of trades per instrument.

## Context
In a trading system, the order in which trades are processed for the same instrument matters. Two simultaneous BUY orders for AAPL must be processed in the order they were received — otherwise position calculations and risk checks produce incorrect results.

## Reasons
- **Kafka ordering guarantee** — Kafka guarantees message ordering within a partition. By partitioning on `instrumentId`, all trades for AAPL go to the same partition and are processed in order.
- **No manual locking** — the alternative is a `ConcurrentTradeExecutor` with per-instrument locks (ThreadPoolExecutor + ConcurrentHashMap). This is complex, error-prone, and doesn't scale across multiple service instances.
- **Horizontal scalability** — adding more partitions and more consumer instances scales throughput without any code changes.
- **Simplicity** — the ordering guarantee is enforced by Kafka infrastructure, not application code.

## Alternatives Rejected
- **Random partitioning** — no ordering guarantee, trades for the same instrument could be processed out of order.
- **Manual thread locking** — complex, doesn't work across multiple service instances, not cloud-native.
- **Single partition** — ordering guaranteed but no parallelism. Throughput bottleneck.

## Consequences
- Hot partitions possible if a small number of instruments account for most trading volume. Mitigated by having enough partitions (12 for trade topics).
- Consumer rebalancing temporarily interrupts processing for affected partitions.
