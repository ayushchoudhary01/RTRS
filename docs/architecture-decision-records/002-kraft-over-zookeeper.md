# 002 — KRaft Mode over ZooKeeper

## Status
Accepted

## Decision
Kafka runs in KRaft (Kafka Raft) mode with no ZooKeeper dependency.

## Context
Historically Kafka required ZooKeeper for cluster metadata management and leader election. Kafka 3.3+ introduced KRaft as a stable alternative, and ZooKeeper was officially removed in Kafka 4.0.

## Reasons
- **Removes external dependency** — ZooKeeper is an entirely separate distributed system that must be operated, monitored, and scaled alongside Kafka. KRaft eliminates this.
- **Simpler operations** — one fewer system to configure, secure, and maintain.
- **Lower RAM usage** — critical for local dev on 8GB machines.
- **Modern standard** — all new Kafka deployments use KRaft. ZooKeeper is end-of-life.
- **No split-brain risk** — ZooKeeper and Kafka could get out of sync during network partitions. KRaft uses a single consensus protocol for everything.

## Alternatives Rejected
- **ZooKeeper** — deprecated, officially removed in Kafka 4.0, adds operational overhead.

## Consequences
- Each broker needs a unique `CLUSTER_ID` generated once at setup.
- All three brokers act as both brokers and controllers (`KAFKA_PROCESS_ROLES: broker,controller`).
