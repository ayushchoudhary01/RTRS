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

## Status — Fixed

Resolved once the first real unit tests were written, because the unresolvable
declarations broke `compileTestJava` for the whole project and no test could run
until they were corrected.

## Fix

The `testcontainers-` prefixed names were correct, but note how they were
confirmed. The Maven Central **search API** returns nothing for
`org.testcontainers:testcontainers-postgresql` and shows `org.testcontainers:postgresql`
topping out at 1.21.3, which makes it look as though the rename never happened.
The search index simply does not carry these artifacts.

The authoritative source is the BOM chain. `spring-boot-dependencies-4.0.6.pom`
pins `<testcontainers.version>2.0.5</testcontainers.version>` and imports
`org.testcontainers:testcontainers-bom`, and that BOM lists the real artifact ids:

```
curl https://repo1.maven.org/maven2/org/testcontainers/testcontainers-bom/2.0.5/testcontainers-bom-2.0.5.pom
```

Applied across all seven services:

```kotlin
// OLD — unresolvable under the Testcontainers 2.0.5 BOM
testImplementation("org.testcontainers:junit-jupiter")
testImplementation("org.testcontainers:kafka")
testImplementation("org.testcontainers:postgresql")

// NEW
testImplementation("org.testcontainers:testcontainers-junit-jupiter")
testImplementation("org.testcontainers:testcontainers-kafka")
testImplementation("org.testcontainers:testcontainers-postgresql")
```

Three services also carry Spring Initializr scaffolding
(`TestcontainersConfiguration`) that needs two dependencies the generated build
file never declared — `@ServiceConnection` and the Grafana LGTM container:

```kotlin
testImplementation("org.springframework.boot:spring-boot-testcontainers")
testImplementation("org.testcontainers:testcontainers-grafana")
```

Those apply to `trade-processor-service`, `trade-ingestion-service` and
`risk-engine-service`. The scaffolding's own imports were already on the 2.x
package layout (`org.testcontainers.postgresql.PostgreSQLContainer`), so only the
missing dependencies were at fault.

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