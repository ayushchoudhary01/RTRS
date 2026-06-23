# 04 — aml-engine-service

## What It Does

Screens every submitted trade against five anti-money-laundering rule
typologies and publishes a verdict directly to Kafka. Structurally a near-
mirror of risk-engine-service — same consume/evaluate/publish shape, no
outbox — but with a richer rule chain and a risk-scoring scheme.

| | |
|---|---|
| Port | 8084 |
| Database | aml_db (5436) |
| Publishes | `aml.cleared.v1`, `aml.flagged.v1` (Avro, direct) |
| Consumes | `trade.submitted.v1` (JSON) |

## Why It Exists

Banks are legally required to screen transactions for money-laundering
patterns — this is not optional in any real BFSI deployment (PMLA in India,
BSA in the US, the EU's AMLD). This service implements five recognizable AML
typologies as deterministic rules, deliberately favoring auditability over
sophistication: a regulator or auditor can be told exactly which rule fired
and why, which a black-box ML model couldn't offer as cleanly. (A planned
ML-based anomaly layer on top of this rule chain, for catching patterns the
rules don't, is documented in `docs/future/003-ml-aml-detection.md`.)

## Key Classes

### The Rule Chain

Unlike risk-engine, every rule here carries an explicit `@Order` annotation,
and `AmlRuleChain` takes `List<AmlRule>` via constructor injection — Spring
delivers the list in `@Order` sequence, which the chain relies on for two
things: stopping at the first breach, and computing a risk score from
*which* rule fired.

**`HighValueTransactionRule`** (`@Order(1)`) — flags if notional exceeds a
configured threshold (`rtrs.aml.high-value-threshold-usd`, default
$500,000).

**`StructuringRule`** (`@Order(2)`) — flags trades with notional in the
$8,000–$10,000 band, just under the $10,000 regulatory reporting threshold.
This is the classic "smurfing" pattern — breaking a large sum into smaller
transactions to dodge mandatory reporting.

**`RoundAmountRule`** (`@Order(3)`) — flags trades over $50,000 whose
notional is an exact multiple of $1,000. Legitimate market trades almost
never land on perfectly round numbers because price × quantity rarely
divides evenly; a suspiciously round amount is a real, recognized red flag.

**`RapidSuccessionRule`** (`@Order(4)`) — flags if the same account has
submitted 3 or more trades within a 5-minute window
(`rtrs.aml.rapid-succession-window-minutes` /
`rtrs.aml.rapid-succession-max-trades`). This detects layering — rapid
transaction volume intended to obscure an audit trail. Implemented as a
count query against this service's own `aml_evaluations` table
(`countByAccountIdAndEvaluatedAtAfter`), not an external call.

**`SanctionedCountryRule`** (`@Order(5)`) — flags if the trade's currency
matches a configured list of sanctioned-jurisdiction currency codes
(`rtrs.aml.sanctioned-countries`, default IR/KP/SY/CU/VE — Iran, North Korea,
Syria, Cuba, Venezuela, mirroring the real OFAC SDN list pattern). This is
matched against currency, not counterparty country, since RTRS has no
counterparty/KYC model.

### Risk Scoring

`AmlRuleChain.execute()` computes a `riskScore` two different ways depending
on outcome:

- **If a rule breaches**: `riskScore = 100 - (rules.indexOf(rule) * 10)`.
  Because the list is `@Order`-sequenced, an earlier-firing rule (lower
  index) produces a *higher* score — `HighValueTransactionRule` firing first
  scores 100, `SanctionedCountryRule` firing fifth scores 60. This encodes
  "structuring/high-value/round-amount are treated as more severe than
  rapid-succession/sanctions" purely through rule ordering, which is a
  subtle but real coupling between `@Order` and the scoring formula — moving
  a rule's position changes its severity score.
- **If every rule passes**: each passed rule adds 5 to a running score,
  so a fully-clean trade still carries a small non-zero score (5 rules × 5 =
  25, matching what's seen in real evaluation logs) rather than a flat 0.

### Persistence and Events

**`AmlEvaluationEntity`** — same idempotency pattern as risk-engine
(`existsByTradeId()` before evaluating), persisted with outcome, risk score,
and the breaching rule/reason if flagged.

**`AmlEvaluationService`** — orchestrates evaluation, persistence, and event
publishing. Builds `AmlClearedEvent` (with `riskScore`, `clearedAt`) or
`AmlFlaggedEvent` (with `riskScore`, `triggeredRules` as a list, `flaggedAt`,
and `requiresManualReview` — set `true` when `riskScore >= 70`).

**`AmlEventProducer`** — wraps the Avro `KafkaTemplate`, same shape as
risk-engine's producer.

## Currency Choice: Deliberately USD, Not INR

Despite this being built for Indian placement interviews, every threshold is
configured in USD with externalized config values, not hardcoded INR
amounts. The reasoning: the target firms (Wells Fargo, BNY, BlackRock) run
global trading desks even from Indian offices, and Indian AML regulation
(PMLA, FIU-IND reporting) maps conceptually onto the same rule typologies
implemented here — the interview answer is "thresholds are externalized so
they can be adjusted per jurisdiction; the system happens to default to USD
because that's the currency the target trading desks operate in."