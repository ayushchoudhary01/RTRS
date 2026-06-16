# 007 — Notification Service

## Current State
No notifications are sent to traders or compliance teams when trades are
executed, rejected, or flagged by AML.

## Planned Improvement
Event-driven notification service with no database dependency.

```
Kafka topics consumed:
- trade.executed.v1    → "Your trade ORDER-XXX has been executed"
- trade.rejected.v1    → "Your trade ORDER-XXX was rejected: [reason]"
- aml.flagged.v1       → "[COMPLIANCE] Trade flagged for manual review"
- risk.breached.v1     → "[RISK] Trade rejected: position limit breached"
```

### Channels
- Email via SendGrid or AWS SES
- WebSocket push for real-time dashboard updates
- Slack webhook for compliance team alerts (AML flags, risk breaches)

### Key Design
- Stateless — no DB needed
- Idempotent — duplicate Kafka events produce duplicate notifications
  (acceptable for notifications, dedupe via Redis if needed)
- Template-based: `templates/trade-executed.html`, `templates/aml-flagged.html`
- Configurable per event type: email only, Slack only, or both

### No-DB Justification
Notification delivery state (sent/failed) is tracked by the email provider
(SendGrid delivery receipts). No need to persist in our own DB.


