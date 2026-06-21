# Kubernetes Deployment — Not Yet Implemented

This folder contains namespace manifests only as placeholders
(`rtrs-infra.yaml`, `rtrs-core.yaml`, `rtrs-compliance.yaml`) reflecting the
intended namespace split for a production deployment, but they are empty.

No Helm charts, deployments, services, or HPA configs exist yet for any RTRS
service. Everything currently runs via Docker Compose for local development.

See [`docs/future/008-kubernetes-deployment.md`](../../docs/future/008-kubernetes-deployment.md)
for the full planned design, including per-service Helm chart structure,
HPA scaling policy (CPU + Kafka consumer lag), and resource limit recommendations
per service.