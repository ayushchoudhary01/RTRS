# 001 — Windows PostgreSQL Port Conflict with Docker

## Symptom
```
FATAL: password authentication failed for user "rtrs_trade"
```
Spring Boot connects successfully from inside the Docker container but fails from the host machine.

## Root Cause
Windows installs PostgreSQL as a system service that starts automatically on port 5432. Docker also tries to bind port 5432. Both processes listen on the same port — Windows PostgreSQL wins and intercepts all connections from Spring Boot running on the host.

Confirmed by:
```bash
netstat -ano | findstr :5432
# Shows two processes on 5432 — Docker and postgres.exe
```

## Solution
Remap all Docker PostgreSQL containers to ports starting from 5433 in `docker-compose.yml`:

```yaml
postgres-trade:
  ports:
    - "5433:5432"

postgres-processor:
  ports:
    - "5441:5432"
```

Update `application.yaml` in each service:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/trade_db
```

## Port Mapping Reference
| Service | Host Port |
|---|---|
| postgres-trade | 5433 |
| postgres-ledger | 5434 |
| postgres-risk | 5435 |
| postgres-aml | 5436 |
| postgres-settlement | 5437 |
| postgres-audit | 5438 |
| postgres-recon | 5439 |
| postgres-auth | 5440 |
| postgres-processor | 5441 |

## Affected
Windows developers with PostgreSQL installed. Not an issue on Linux or Mac.