# Privacy model

## Processing modes

| Mode | Where pixels go | Status |
|---|---|---|
| `LOCAL_ONLY` | App-private storage on the device. Nothing leaves the device. | **Default, and the only mode implemented** |
| `CLOUD_ANALYSIS` | Selected photos are uploaded to the project backend for analysis. | Reserved, not implemented |

`ProcessingMode` is stored on every `ScanSession` and in every result's provenance.

## Guarantees in the current build

- The manifest declares **no `INTERNET` permission**, so the app cannot upload anything.
- **No `CAMERA` permission yet** (CameraX arrives in Phase 3).
- `allowBackup="false"` and data-extraction rules exclude all app data from cloud backup and
  device-to-device transfer.
- Photos (from Phase 3) will be written to app-private storage, not the shared gallery,
  unless the user explicitly exports them.

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
