# 0003 — Local-first processing; no network permission in the MVP

## Context
Card photos are personal data. Early testing should cost nothing in cloud spend, and users must never be surprised by uploads.

## Decision
- `ProcessingMode.LOCAL_ONLY` is the default and the only implemented mode. `CLOUD_ANALYSIS` is reserved.
- The manifest has no `INTERNET` permission. Backup and device transfer are disabled.
- Any future upload requires explicit per-scan consent, and dataset contribution needs a separate consent (docs/privacy.md).
- `InspectionPipeline` is one contract with local and (future) remote implementations.

## Consequences
- The MVP runs entirely offline with zero cloud cost.
- Heavy future analysis can move server-side without changing the UI contract.
- Adding network access becomes a deliberate, reviewable change.
