# 008 — Kubernetes Deployment

## Current State
All services run locally via IntelliJ + Docker Compose. No production deployment
configuration exists.

## Planned Improvement
Helm charts for each service with production-grade configuration.

### Per-Service Helm Chart Structure
```
charts/
├── trade-ingestion/
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
│       ├── deployment.yaml
│       ├── service.yaml
│       ├── configmap.yaml
│       ├── hpa.yaml          ← HorizontalPodAutoscaler
│       └── poddisruptionbudget.yaml
├── risk-engine/
├── aml-engine/
├── ledger-service/
└── ...
```

### HPA Configuration
```yaml
# risk-engine scales on CPU and Kafka consumer lag
metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        averageUtilization: 70
  - type: External
    external:
      metric:
        name: kafka_consumer_lag
      target:
        value: 1000
```

### Resource Limits (per service)
| Service | CPU Request | CPU Limit | Memory Request | Memory Limit |
|---|---|---|---|---|
| trade-ingestion | 250m | 500m | 512Mi | 1Gi |
| risk-engine | 500m | 1000m | 512Mi | 1Gi |
| aml-engine | 500m | 1000m | 512Mi | 1Gi |
| ledger-service | 250m | 500m | 512Mi | 1Gi |
| trade-processor | 500m | 1000m | 768Mi | 1.5Gi |

### Secrets Management
- Kubernetes Secrets for JWT secret, DB passwords
- External Secrets Operator for HashiCorp Vault integration
- Never store secrets in Helm values files


