# 011 — Multi-Currency Support

## Current State
All risk and ledger calculations assume USD. `currency` field is stored and
passed through the system but not used for currency conversion. INR trades
are processed at face value without conversion.

## Planned Improvement

### FX Rate Service
```
fx-rate-service (port 8093)
↓
Fetches from ECB / RBI / Open Exchange Rates API
↓
Caches in Redis with 5-minute TTL
↓
Exposes: GET /api/v1/fx/rate?from=INR&to=USD
```

### Risk Engine Changes
`RiskContext.notionalUsd` currently assumes currency is USD.
With multi-currency:

```java
BigDecimal notionalUsd = notionalInOriginalCurrency
    .multiply(fxRateService.getRate(currency, "USD"));
```

All risk rules evaluate against USD notional regardless of trade currency.

### Ledger Changes
- `LedgerEntry` stores amount in original currency + USD equivalent
- `AccountBalance` maintains balance per currency
- New field: `usdEquivalent` for cross-currency P&L calculation

### AML Changes
- `HighValueTransactionRule` threshold in USD regardless of currency
- `SanctionedCountryRule` expands to check currency AND counterparty country

### Indian Market Support
- INR/USD rate from RBI reference rate (published daily)
- SEBI lot sizes for NSE/BSE instruments
- T+1 settlement for Indian equities (post-2024 SEBI mandate)


