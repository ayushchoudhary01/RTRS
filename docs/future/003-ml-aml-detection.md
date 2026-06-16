# 003 — ML-Based AML Anomaly Detection

## Current State
`aml-engine-service` uses a deterministic rule chain:
1. HighValueTransactionRule
2. StructuringRule
3. RoundAmountRule
4. RapidSuccessionRule
5. SanctionedCountryRule

Rules catch known patterns. Unknown patterns are missed.

## Planned Improvement
Add a Python FastAPI microservice using Isolation Forest for unsupervised
anomaly detection on trade patterns.

```
trade.submitted.v1
↓
Rule Chain (deterministic, fast)
↓
If CLEARED → ML Anomaly Scorer (probabilistic)
↓
If anomaly score > threshold → escalate to manual review
↓
aml.cleared.v1 or aml.flagged.v1
```

## Architecture
- Separate Python service: `aml-anomaly-service` on port 8092
- Isolation Forest trained on historical cleared trades
- Features: notional, velocity, time-of-day, instrument concentration, account age
- RTRS calls it synchronously via REST after rule chain passes
- Returns: `anomalyScore (0.0-1.0)`, `isAnomaly (bool)`, `contributingFeatures`
- Threshold configurable: default 0.85

## Why Isolation Forest
- Unsupervised — no labeled fraud data needed
- Works well on tabular financial data
- Explainable — can identify which features contributed to anomaly score
- Fast inference — sub-millisecond for single trade scoring

## Tech Stack
- Python 3.14 + FastAPI
- scikit-learn Isolation Forest
- pandas for feature engineering
- Model retrained nightly on last 30 days of cleared trades
- Model artifacts stored in `/models/` directory


