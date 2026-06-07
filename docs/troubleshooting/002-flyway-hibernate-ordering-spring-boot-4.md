# 002 — Flyway Runs After Hibernate Validation in Spring Boot 4

## Symptom
```
Schema validation: missing table [outbox_events]
org.hibernate.tool.schema.spi.SchemaManagementException
```
Service fails to start even though Flyway migration file exists and is correct.

## Root Cause
In Spring Boot 4, Hibernate schema validation runs before Flyway migrations execute. With `ddl-auto: validate`, Hibernate checks that all JPA entity tables exist before Flyway has a chance to create them.

This only happens when:
- The database exists but has no tables (empty DB after volume recreation)
- Spring Boot 4 initialization order changed from previous versions

## Solution
Add to `application.yaml`:
```yaml
spring:
  jpa:
    defer-datasource-initialization: true
```

This defers JPA datasource initialization until after Flyway has run.

## First-Time Setup Workaround
If the database already exists but is empty (e.g. after `docker-compose down -v` and restart), run the migration SQL manually once:

```powershell
# PowerShell (Windows)
Get-Content "path/to/V1__create_trade_tables.sql" | docker exec -i rtrs-postgres-trade psql -U rtrs_trade -d trade_db
```

After this, Flyway will track the migration in `flyway_schema_history` and subsequent startups work normally.

## Note for Fresh Clones
Anyone cloning the repo for the first time will not hit this issue — Flyway runs correctly on a completely fresh empty database.