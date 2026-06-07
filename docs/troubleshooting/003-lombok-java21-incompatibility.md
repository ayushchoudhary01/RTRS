# 003 — Lombok Incompatibility with Java 21.0.11 in Maven

## Symptom
```
Fatal error compiling: java.lang.ExceptionInInitializerError
Caused by: java.lang.NoSuchFieldException: com.sun.tools.javac.code.TypeTag :: UNKNOWN
at lombok.javac.Javac.<clinit>
```
Maven build fails for shared-libs modules using Lombok annotations.

## Root Cause
Lombok versions up to 1.18.36 use internal javac APIs (`com.sun.tools.javac.code.TypeTag`) that were changed or removed in Java 21.0.11 (a newer patch release). The `TypeTag.UNKNOWN` field no longer exists in this JDK build.

This affects Maven builds specifically. Gradle + IntelliJ handle Lombok correctly because they use a different annotation processing pipeline.

## Solution
Remove Lombok entirely from `shared-libs` Maven modules. These are small library modules with few classes — write getters, builders, and loggers manually:

```java
// Instead of @Slf4j
private static final Logger log = LoggerFactory.getLogger(ClassName.class);

// Instead of @Builder
public static class Builder { ... }

// Instead of @Getter
public String getField() { return field; }
```

Lombok still works correctly in Spring Boot services (Gradle-based) — only removed from Maven shared-libs.

## Why Not Fix Lombok Version
Multiple Lombok versions (1.18.32, 1.18.34, 1.18.36) were tested — all fail with Java 21.0.11. The `jvm.config` workaround with `--add-opens` also did not resolve the issue. Removing Lombok from shared-libs was the cleanest solution.

## Affected
Maven projects on Java 21.0.11 (Oracle JDK). Not an issue on Java 21.0.x earlier patch releases or on Gradle.