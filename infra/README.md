# Infrastructure — DESIGN ONLY

**No resources exist and none may be created without explicit approval.** No credentials,
state files or `.tfvars` belong in this repository (see .gitignore).

## Intended components (future)

| Component | Service | Notes |
|---|---|---|
| API | ECS Fargate (or Lambda + API Gateway for low volume) | Behind ALB/API Gateway, TLS only |
| Photo storage | S3 | Block Public Access ON, SSE-KMS, bucket policy denies non-TLS, presigned PUT only, lifecycle expiry for analysis-only uploads |
| Queue | SQS (+ DLQ) | Analysis jobs |
| Worker | ECS Fargate | CPU OpenCV. GPU service only if a model requires it |
| Database | RDS PostgreSQL | Private subnets, encrypted, automated backups |
| Secrets | AWS Secrets Manager | Never in code or the app |
| Observability | CloudWatch logs/metrics, alarms on DLQ depth and cost | |
| IaC | Terraform (or CDK) | Remote state in an encrypted, private backend |

## Guardrails

- Budgets and alerts are configured *before* any paid resource.
- Separate dev and prod accounts, least-privilege IAM, no long-lived access keys.
- No public buckets, ever.
