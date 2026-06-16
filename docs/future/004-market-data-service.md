# 004 — Market Data Service and Real VaR Computation

## Current State
`VaRRule` and `ConcentrationRule` in `risk-engine-service` use a hardcoded
portfolio baseline of $10,000,000. VaR is computed as a simple percentage of
notional rather than from historical price volatility.

## Planned Improvement

### market-data-service (port 8091)
- Consumes real-time price feeds (Alpha Vantage API or Yahoo Finance)
- Caches latest prices in Redis with 1-minute TTL
- Exposes: `GET /api/v1/market/price/{instrumentId}`
- Exposes: `GET /api/v1/market/volatility/{instrumentId}?window=30d`
- Publishes `market.price.updated.v1` to Kafka on significant price moves

### Real VaR Computation
```
VaR = notional × daily_volatility × confidence_factor × sqrt(holding_period)

Where:
- daily_volatility = stddev of daily returns over 30-day window
- confidence_factor = 1.645 (95% confidence) or 2.326 (99% confidence)
- holding_period = 1 day (standard for trading book)
```

### RiskEvaluationService changes
- Call `market-data-service` via REST to get current volatility
- Pass to `VaRRule` as part of `RiskContext`
- VaR threshold becomes a percentage of total portfolio VaR, not hardcoded baseline

## Benefits
- Risk evaluation becomes accurate rather than approximate
- Position limits become dynamic based on current market volatility
- High-volatility periods automatically tighten risk limits


