# 010 — Regulatory Reporting

## Current State
No regulatory reporting exists. AML-flagged trades generate a log entry and
a Kafka event but no formal report is filed. Executed trades are recorded in
the ledger but no regulatory submission is generated.

## Planned Improvement

### MiFID II Trade Reporting (EU)
Every executed trade must be reported to an Approved Reporting Mechanism (ARM)
within T+1:

```
trade.executed.v1
↓
regulatory-reporting-service
↓
MiFID II XML format
↓
ARM submission (e.g. DTCC, Bloomberg BTCA)
```

Fields required: LEI (Legal Entity Identifier), ISIN, price, quantity,
venue, timestamp, trader ID, counterparty.

### FIU-IND SAR Filing (India)
Suspicious Activity Reports for AML-flagged trades:

```
aml.flagged.v1
↓
regulatory-reporting-service
↓
FIU-IND CTR/STR format
↓
goAML portal submission
```

SAR must be filed within 7 days of detection under PMLA 2002.

### SEBI Trade Reporting
For trades on Indian exchanges:
- Equity trades: reported to NSE/BSE within 24 hours
- FII/FPI trades: reported to SEBI within T+1

### Implementation
- `regulatory-reporting-service` — no DB write needed, stateless transformer
- Consumes `trade.executed.v1` and `aml.flagged.v1`
- Formats and submits to respective regulatory endpoints
- Stores submission receipts in `regulatory_submissions` table for audit

