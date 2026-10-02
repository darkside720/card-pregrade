# Privacy model

## Processing modes

| Mode | Where pixels go | Status |
|---|---|---|
| `LOCAL_ONLY` | App-private storage on the device. Nothing leaves the device. | **Default, and the only mode implemented** |
| `CLOUD_ANALYSIS` | Selected photos are uploaded to the project backend for analysis. | Reserved, not implemented |

`ProcessingMode` is stored on every `ScanSession` and in every result's provenance.

## Current implementation

- **`CAMERA`** is the only dangerous permission. It is requested at runtime, only when the
  guided capture screen needs it.
- **Photos are stored in app-private storage** (`<filesDir>/scans/<sessionId>/`), not the
  shared gallery. Session and capture metadata (including EXIF-derived camera settings) are
  stored in the app's local Room database. The app has no export feature.
- **No `INTERNET` permission** is declared, so the app cannot open network connections.
- **No upload path exists.** The capture and results workflow contains no HTTP/REST/GraphQL
  client, AWS or Firebase SDK, analytics or crash-reporting SDK, or remote ML service.
  Photographs are not uploaded.
- `allowBackup="false"` and data-extraction rules exclude all app data from cloud backup and
  device-to-device transfer.

## Rules for any future cloud feature

1. **Never silent.** Uploads happen only after an explicit, per-scan user action that names
   what is uploaded and why.
2. **Consent is recorded** (timestamp, scope, app version) and revocable. Revocation deletes
   server-side copies.
3. **Minimum data.** Send only the photos needed; strip EXIF location. Device metadata is
   sent only if the user opted in to dataset contribution.
4. **Separate consents** for (a) cloud analysis of one scan and (b) contributing photos and
   outcomes to the training dataset.
5. **Storage:** private S3 buckets only (Block Public Access on, SSE-KMS encryption,
   presigned upload URLs with short expiry, lifecycle expiry for analysis-only uploads).
6. **No secrets in the app.** The app authenticates with user-scoped tokens; no API keys or
   AWS credentials are embedded in Android source or resources.
7. **Adding the `INTERNET` permission** is a reviewed change that must link to this document.

## Personal data in scope

Card photos (may show hands, rooms, reflections), device metadata, grading certification
numbers, submission dates. Cert numbers can be linked to public registries, so they are
treated as personal data and are only sent to a server with dataset consent.

## Repository hygiene

- `.gitignore` excludes `.env`, keystores, `local.properties`, credentials, IDE state, build
  output, virtual environments and non-synthetic images under `test-data/`.
- Real card photos are never committed without the owner's permission (see test-data/README.md).
