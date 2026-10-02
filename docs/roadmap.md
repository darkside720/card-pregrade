# Roadmap

Each phase ends with a stop-and-review before the next begins.

## Phase 0 — Discovery ✅
Toolchain inventory, directory check.

## Phase 1 — Architecture ✅
Docs, ADRs, domain models, CV/grading/data interfaces.

## Phase 2 — Android skeleton ✅
Multi-module Gradle project, Compose navigation, 7 screens + calibration, DEMO/MOCK results UI,
Room schema, unit + instrumented test scaffolding.

## Phase 3 — Camera MVP ✅
Delivered:
- CameraX rear-camera Preview + ImageCapture at the highest available resolution
  (maximize-quality mode); tap-to-focus, exposure compensation and torch toggle.
- Runtime `CAMERA` permission flow with rationale and a permanently-denied path.
- Card guide overlay on the live preview (`cardGuideRect`).
- Four-step protocol — front straight, back straight, front angled left, front angled right —
  with per-step review, accept, retake, and resume of an interrupted session.
- Capture lifecycle in app-private storage: `pending/` candidate → accepted file per step;
  `CapturedImage` + EXIF/device metadata (ISO, exposure time, focal length, orientation)
  persisted in Room.
- Capture-quality checks (`CaptureQualityAnalyzer`): resolution, sharpness (Laplacian variance),
  exposure and highlight clipping on a 1024 px downscale. Only undecodable or far-too-low
  resolution photos are blocking; other problems need an explicit, recorded user override.
  Thresholds are provisional.
- Calibration screen shows real accepted captures and their capture-quality measurements.
- Instrumented tests using a fake camera source.
- Hardware validation on a Google Pixel 8 Pro (Android 17 / API 37): a real four-photo session
  produced four 4080 × 3072 JPEGs.

Not part of Phase 3: exposure *lock* (only compensation), any card analysis, any grading.

## Phase 4 — CV foundation
- `:core:cv-opencv` module (OpenCV Android SDK via Maven).
- CardDetector (contours + polygon approximation + aspect filter), PerspectiveCorrector,
  ImageNormalizer, ImageQualityAnalyzer (blur, glare, exposure, coverage, perspective).
- CenteringAnalyzer (border detection on corrected image; Unknown for borderless).
- Calibration screen backed by real `StageDiagnostics`.
- Synthetic fixture expansion; `cv-reference` parity tests.
- Calibrate quality-gate thresholds from real captures.
- Persist full `InspectionResult` payload (choose serialization).

## Phase 5 — Defect foundation
- CornerAnalyzer, EdgeAnalyzer, WhiteningDetector (conservative; SUPPORTED only after fixture
  and real-photo validation).
- Defect overlay on real corrected images with tap-to-zoom into the photo.

## Phase 6 — Pre-grade
- First provisional `GradingRuleSet` (centering from published tolerances; others provisional).
- PSA and BGS ranges with limiting factors; candidate indicators.
- Professional-grade entry UI and `PredictionComparison` recording.
- Target result presentation (continuous condition score, grading-company range, 10-candidate
  assessment, distance to next grade, limiting factors) is described in
  [grading-methodology.md](grading-methodology.md#future-grading-presentation-design-not-implemented).

## Later
- Surface analysis from multi-angle captures (experimental).
- Back angled, macro corner and edge captures.
- Card identification (optional metadata).
- Backend (FastAPI/S3/SQS/worker/PostgreSQL) behind explicit consent.
- iOS (evaluate Kotlin Multiplatform for pure modules).
- Dependency/AGP upgrade as a dedicated change.
