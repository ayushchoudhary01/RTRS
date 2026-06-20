# 009 — Ambiguous Imports: Same Simple Name, Wrong Package

## Symptom
Two classes of bugs that compile cleanly but behave wrong at runtime, both
caused by importing a same-named class from the wrong package:

**Jackson API mismatch**
```java
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
...
payload.get("tradeId").asString();
```
`tools.jackson.databind` is Jackson 3.x's namespace. RTRS uses Jackson 2.x
(`com.fasterxml.jackson.databind`) everywhere, pulled in via Spring Boot's
dependency management. Jackson 2.x's `JsonNode` exposes `.asText()`, not
`.asString()` — that method name is Jackson 3.x-only.

**Wrong exception class**
```java
import org.apache.kafka.common.errors.ResourceNotFoundException;
```
instead of the project's own:
```java
import com.rtrs.<service>.exception.ResourceNotFoundException;
```
Kafka's version is a valid `RuntimeException` subtype, so it compiles. But a
`@RestControllerAdvice`'s `@ExceptionHandler(ResourceNotFoundException.class)`
only matches the exact class it's registered against — throwing Kafka's
version silently bypasses the custom handler and falls through to a generic
500 instead of the intended 404.

## Root Cause
Both bugs share the same shape: a class name that exists in more than one
library on the classpath. IDE autocomplete or import suggestions can offer
the wrong one first, especially for generically-named classes —
`ResourceNotFoundException`, `Builder`, `Result`, `Pair` are all common enough
that a third-party library is statistically likely to define one too.

## Fix
Always check the fully qualified import, not just the simple class name,
when:
- the class name is generic enough that multiple libraries could plausibly
  define it
- a project has its own exception hierarchy alongside third-party libraries
  that also throw exceptions (Kafka, Spring, etc. all ship their own
  `ResourceNotFoundException`-style classes)
- mixing major versions of a library is possible on the classpath (e.g.
  Jackson 2.x vs 3.x, where method names like `.asText()` vs `.asString()`
  differ between versions but neither fails loudly at compile time if the
  wrong version happens to be present)

## Applies To
This is a recurring pattern check across the codebase, not a single incident —
worth a quick visual scan of imports in any new consumer or controller class
before assuming autocomplete picked the right one.