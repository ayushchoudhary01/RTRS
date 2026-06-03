# 009 — Custom AML Rule Chain over Drools

## Status
Accepted

## Decision
The AML Engine uses a custom rule chain built with the Strategy + Chain of Responsibility design patterns instead of a rules engine like Drools.

## Context
AML compliance requires evaluating multiple rules against each transaction — structuring detection, velocity checks, sanctions screening, geographic risk. We needed a flexible, extensible rule evaluation mechanism.

## Reasons
- **Simplicity** — Drools requires a separate rule language (DRL), a KieSession lifecycle, and significant operational overhead. Our rules are pure Java — readable, testable, debuggable.
- **Type safety** — Drools rules are strings evaluated at runtime. Our rules are compiled Java with full IDE support and compile-time checks.
- **Extensibility** — adding a new rule means implementing the `AmlRule` interface and registering it in the chain. No Drools configuration files to update.
- **Testability** — each rule is an independent Java class with unit tests. No Drools test harness required.
- **Performance** — no rule engine overhead. Rules execute as plain Java method calls.

## Design
- `AmlRule` interface: `RuleResult evaluate(AmlContext context)`
- `AmlRuleChain`: ordered list of rules, executes each in sequence, aggregates results
- `RiskScoringEngine`: weighted score aggregation across all triggered rules (0-100, threshold 70)
- Rules: `StructuringRule`, `VelocityRule`, `RoundAmountRule`, `CounterpartyRule`, `GeographicRiskRule`

## Alternatives Rejected
- **Drools** — heavy, complex, XML-based configuration, steep learning curve, legacy in modern fintechs.
- **Easy Rules** — lightweight but limited, no built-in scoring or context propagation.

## Consequences
- New rules require a code deployment (no hot-reload). Acceptable for a trading system where rule changes require testing anyway.
