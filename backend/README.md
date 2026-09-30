# Backend — DESIGN ONLY

Nothing here is implemented, deployed or provisioned. The MVP runs entirely on the device
(ADR 0003). This document captures the intended design so the Android contracts stay compatible.

## When a backend becomes necessary

- Analysis too heavy for devices (e.g. multi-angle surface models on GPU).
- Consented dataset collection (photos + predictions + professional outcomes).
- Cross-device history/sync (optional, later).

## Stack

Python · FastAPI · PostgreSQL · S3 · AWS (SQS, ECS/Fargate; Lambda for light tasks; GPU only if ML demands it).

## Flow

```
Android ──(explicit per-scan consent)──► API (FastAPI)
   1. POST /v1/analyses               → analysis id + presigned S3 PUT URLs (short expiry)
   2. PUT photos directly to S3       (private bucket, SSE-KMS)
   3. POST /v1/analyses/{id}/submit   → enqueue job on SQS
                                           │
                                   CV/ML worker (ECS/Fargate; GPU service only if needed)
                                   - same pipeline contract as on-device (InspectionPipeline)
                                   - cv-reference core + versioned rule set
                                           │
                                       PostgreSQL (results, defects, versions)
   4. GET /v1/analyses/{id}           → InspectionResult (same schema as the app model)
```

## Data model (mirrors :core:model)

`cards, scan_sessions, captured_images (S3 keys, device metadata), inspection_results,
card_defects, professional_grades, prediction_comparisons, consents` — every analysis row with
pipeline/algorithm versions.

## API principles

- User-scoped auth tokens. No static API keys in the app.
- Versioned endpoints (`/v1`). The result payload is versioned independently.
- Idempotent submission, and async polling or push for results.
- `DELETE /v1/analyses/{id}` removes photos and results (consent revocation).

## Not now

No code, no Terraform, no AWS resources until explicitly approved in a later phase.
