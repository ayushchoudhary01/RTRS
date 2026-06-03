# 003 — Database Per Service

## Status
Accepted

## Decision
Every microservice owns its own dedicated PostgreSQL database. No service may directly query another service's database.

## Context
In a microservices architecture, sharing a database between services creates tight coupling at the data layer — the exact coupling microservices are meant to avoid.

## Reasons
- **Independent deployability** — a schema change in one service cannot break another service.
- **Bounded context enforcement** — each service is the sole owner of its data. Cross-service data access goes through events or REST APIs only.
- **Independent scaling** — the ledger database can be scaled independently of the trade database.
- **Failure isolation** — a corrupted or unavailable trade database does not affect the ledger or AML service.

## Alternatives Rejected
- **Shared database** — creates tight schema coupling, prevents independent deployments, single point of failure.
- **Shared schema, separate tables** — still couples services at the database level.

## Consequences
- No cross-service JOIN queries — data aggregation must happen at the application layer via events.
- Distributed transactions require the Saga pattern instead of ACID transactions.
- More database instances to manage locally (8 PostgreSQL containers).
