# 006 — API Gateway

## Current State
Clients call `trade-ingestion-service` directly on port 8086. JWT validation
happens inside each service via `rtrs-security` shared lib. No rate limiting,
no circuit breakers at the entry point.

## Planned Improvement
Spring Cloud Gateway as the single entry point for all external traffic.

```
Client
↓
api-gateway (port 8080)
↓ JWT validation (once, at gateway)
↓ Rate limiting
↓ Request routing
↓
trade-ingestion-service / ledger-service / etc.
```

### Key Features
- JWT validation at gateway — individual services no longer need rtrs-security
- Rate limiting: 100 req/min per client by default, configurable per route
- Circuit breaker: Resilience4j per downstream service
- Request/response logging with traceId injection
- Route configuration via `application.yml` — no code changes to add routes

### Routes
```yaml
routes:
  - id: trade-ingestion
    uri: lb://trade-ingestion-service
    predicates:
      - Path=/api/v1/trades/**
    filters:
      - name: CircuitBreaker
      - name: RateLimiter
        args:
          redis-rate-limiter.replenishRate: 100
          redis-rate-limiter.burstCapacity: 200
```

### Load Balancing
Spring Cloud LoadBalancer with service discovery via Eureka or static config.

## Benefits
- Single TLS termination point
- Consistent auth, rate limiting, and observability across all services
- Individual services become simpler — no security boilerplate

