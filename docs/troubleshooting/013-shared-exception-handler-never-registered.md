# 013 — Shared `GlobalExceptionHandler` In `rtrs-common` Was Never Registered

## Symptom
Every service declares a dependency on `rtrs-common`, which ships a
`@RestControllerAdvice` that maps `DomainException` to the HTTP status the
exception carries. It never ran. A `DomainException` thrown in any service that
did not define its own advice fell through to the container default and came back
as a generic 500:

```json
{"errorCode":"INTERNAL_ERROR","message":"An unexpected error occurred","success":false}
```

Nothing failed at startup, so this looked like working shared infrastructure.

## Root Cause
`@SpringBootApplication` component-scans **the package of the annotated class and
below**. Every service lives under its own base package:

```
com.rtrs.ledgerservice          <- @SpringBootApplication here, scans this subtree
com.rtrs.tradeingestionservice
com.rtrs.common                 <- rtrs-common lives here, a SIBLING
```

`com.rtrs.common` is never beneath any service's base package, so scanning never
reaches it. The advice class was on the classpath, compiled and correct, and
simply never became a bean.

Being on the classpath is not the same as being registered. A `@RestControllerAdvice`
that is never instantiated fails silently — there is no missing-bean error, because
nothing injects it.

## Fix
Register it through Spring Boot's auto-configuration mechanism, which is the
supported way for a library to contribute beans to a host application without
relying on the host's component scan.

`CommonAutoConfig` in `com.rtrs.common.config`:

```java
@AutoConfiguration
public class CommonAutoConfig {

    @Bean
    public GlobalExceptionHandler commonGlobalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
```

Listed in
`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.rtrs.common.config.CommonAutoConfig
```

Two details that are not optional:

**1. The bean name must not be `globalExceptionHandler`.** `settlement-service` and
`reconciliation-service` component-scan their own classes of that name, and the
clash fails startup with `BeanDefinitionOverrideException` (see 011 for the same
failure from a different cause). Hence `commonGlobalExceptionHandler()`.

**2. Advice ordering must be explicit.** The shared handler has a catch-all
`@ExceptionHandler(Exception.class)`. Spring consults advices in `@Order` sequence,
so without ordering the shared catch-all could win over a service's specific
handler and turn its deliberate 404 into a 500 — a regression introduced *by*
registering the handler. Resolved by pushing the shared one last and pinning the
local ones first:

```java
@Order(Ordered.LOWEST_PRECEDENCE)   // rtrs-common
@Order(0)                           // settlement-service, reconciliation-service
```

## Lesson
A shared library cannot assume the host will scan it. If the library's package is
not under the application's base package — which it usually is not, and should not
have to be — it must either ship auto-configuration or require an explicit
`@ComponentScan`, and auto-configuration is the one that does not leak the
library's package name into every consumer.

The wider point is that this class of bug is invisible. The code existed, compiled,
was dependency-declared and looked used. Only checking an actual error response
revealed that the shared handler had never run.

## Affected
- `GlobalExceptionHandler` in `rtrs-common`
- new `CommonAutoConfig` in `rtrs-common`
- local `GlobalExceptionHandler` in `settlement-service` and `reconciliation-service`
