# 010 — Spring Batch 6.0 API Changes: Three Migration Issues

## Context
Spring Boot 4 pulls in Spring Batch 6.x, which underwent a significant API
reorganization (released mid-2025). Most tutorials, Stack Overflow answers,
and even some AI-generated code online still reflect the Spring Batch 5.x
API, which compiles against 6.x in a deprecated-but-working form, or in some
cases doesn't compile at all. Three separate issues were hit building
`EodSettlementJob` and `SettlementScheduler`.

## Issue 1 — Wrong package for ListItemReader
**Symptom:** import error for
```java
import org.springframework.batch.item.support.ListItemReader;
```

**Cause:** that package path doesn't exist at any Spring Batch version. The
correct package never moved.

**Fix:**
```java
import org.springframework.batch.infrastructure.item.support.ListItemReader;
```

## Issue 2 — Deprecated chunk(int, PlatformTransactionManager)
**Symptom:** compiler warning —
`'chunk(int, PlatformTransactionManager)' is deprecated since version 6.0 and marked for removal`

**Cause:** Spring Batch 6.0 made the transaction manager configuration on
chunk-oriented steps optional and split it into its own builder method,
deprecating the combined overload (scheduled for removal in 7.0).

**Fix — old (v5 style):**
```java
.chunk(10, transactionManager)
```
**Fix — new (v6 style):**
```java
.chunk(10)
.transactionManager(transactionManager)
```

## Issue 3 — JobLauncher deprecated in favor of JobOperator
**Symptom:** compiler warning —
`'org.springframework.batch.core.launch.JobLauncher' is deprecated since version 6.0 and marked for removal`

**Cause:** in Spring Batch 6.0, `JobOperator` now extends `JobLauncher`,
making a separate `JobLauncher` bean unnecessary. `JobLauncher` itself is
deprecated for removal in 6.2+.

**Fix:**
```java
// old
private final JobLauncher jobLauncher;
...
jobLauncher.run(job, params);

// new
private final JobOperator jobOperator;
...
jobOperator.start(job, params);
```
Note the method name also changes from `.run()` to `.start()` — this isn't
just a type rename at the call site. Spring Boot auto-configures the
`JobOperator` bean automatically once Spring Batch is on the classpath; no
additional configuration bean is needed.

## Lesson
When using a library that had a major version bump recently, verify fixes
against the library's own official migration guide and current javadocs
rather than the first matching code snippet found online — the public web is
still saturated with pre-6.0 Spring Batch examples that will compile (often
with deprecation warnings, sometimes not at all) but represent an API that's
actively being removed.