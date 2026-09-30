# Roadmap

Each phase ends with a stop-and-review before the next begins.

## Phase 0 — Discovery ✅
Toolchain inventory, directory check.

## Phase 1 — Architecture ✅
Docs, ADRs, domain models, CV/grading/data interfaces.

## Phase 2 — Android skeleton ✅
Multi-module Gradle project, Compose navigation, 7 screens + calibration, DEMO/MOCK results UI,
Room schema, unit + instrumented test scaffolding.

## Phase 3 — Camera MVP (next)
- CameraX Preview + ImageCapture at max resolution; tap-to-focus, exposure lock, torch toggle.
- `CAMERA` permission flow with rationale.
- Card guide overlay on the live preview (reuse `cardGuideRect`).
- Front, back, left-light, right-light capture steps; retake; per-step review.
- Save to app-private storage; persist `CapturedImage` with `DeviceMetadata` (Camera2 interop
  for ISO/exposure/focal length).
- Basic quality gate: resolution check and Laplacian-variance blur on a downscaled frame
  (warn-only until calibrated), wired to `CaptureFlowState` rejection.
- Instrumented tests using a fake camera/image source.

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

## Later
- Surface analysis from multi-angle captures (experimental).
- Back angled, macro corner and edge captures.
- Card identification (optional metadata).
- Backend (FastAPI/S3/SQS/worker/PostgreSQL) behind explicit consent.
- iOS (evaluate Kotlin Multiplatform for pure modules).
- Dependency/AGP upgrade as a dedicated change.
