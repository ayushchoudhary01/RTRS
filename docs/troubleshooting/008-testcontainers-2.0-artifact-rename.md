# 008 — Testcontainers Test Dependencies Unresolvable After Spring Boot 4.0.6

## Symptom
Gradle sync fails on `reconciliation-service` with:
Could not find org.testcontainers:junit-jupiter:.

Could not find org.testcontainers:kafka:.

Could not find org.testcontainers:postgresql:.
Note the trailing `:.` — Gradle is resolving an empty version string, not failing
to find a repository.

## Root Cause
Spring Boot's dependency-management BOM (`spring-boot-dependencies`) manages a
`testcontainers.version` property, which is what lets every service declare
`testImplementation("org.testcontainers:postgresql")` etc. without an explicit
version — the BOM supplies it.

As of Spring Boot 4.0.x, that managed version is Testcontainers **2.0.5**.
Testcontainers 2.0 is a breaking release: every module artifact was renamed to
require a `testcontainers-` prefix, and container classes moved to
module-specific packages. The old coordinates
(`org.testcontainers:postgresql`, `org.testcontainers:kafka`,
`org.testcontainers:junit-jupiter`) are pre-2.0 names that the 2.0.5 BOM does
not manage a version for — hence the empty version Gradle reports.

This is a **pre-existing bug across every service in the repo**, not something
introduced by `reconciliation-service`. `ledger-service` and
`trade-processor-service` declare the exact same old-style coordinates. They
have not visibly failed yet, almost certainly because their local Gradle/build
caches still hold a dependency resolution from before the project was upgraded
to Spring Boot 4.0.6. A clean clone or a `--refresh-dependencies` run would
likely surface the same failure there too.

## Status — Deferred, Not Fixed
No service in this project currently has any Testcontainers-based integration
test. The broken dependency declarations don't block building or running any
service — they only block `./gradlew test` if a test actually tries to use
Testcontainers, which none currently do.

Decision: leave the dependency declarations as-is for now rather than spend
time chasing the correct Testcontainers 2.0 artifact names with no test to
verify against. Revisit when real integration tests are written.

## Fix (when revisited)
Replace old-style coordinates with the `testcontainers-` prefixed equivalents,
e.g.:
```kotlin
// OLD (broken under Testcontainers 2.0.5)
testImplementation("org.testcontainers:junit-jupiter")
testImplementation("org.testcontainers:kafka")
testImplementation("org.testcontainers:postgresql")

// NEW (verify exact artifact IDs against Maven Central before applying)
testImplementation("org.testcontainers:testcontainers-junit-jupiter")
testImplementation("org.testcontainers:testcontainers-kafka")
testImplementation("org.testcontainers:testcontainers-postgresql")
```
Exact new artifact IDs should be confirmed against Maven Central at fix time,
not assumed from this note.

## Affected Services
All services with `testImplementation("org.testcontainers:...")` declarations
using pre-2.0 coordinates: `ledger-service`, `trade-processor-service`,
`reconciliation-service`, and likely any other service scaffolded from the
same template.

## Interview Talking Point
Demonstrates dependency-version drift awareness: a major version bump in a
transitive BOM (Spring Boot → Testcontainers) silently broke test dependencies
across the whole project, and the bug was caught while building a *new*
service, not the ones that introduced it — because their stale build caches
were masking it. Good example of "consciously deferred technical debt" vs.
"silent gap": documented, scoped, and with a known fix path, rather than left
to surprise someone later.