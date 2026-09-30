# Architecture

## 1. Product objective

Help trading-card collectors decide *whether and where* to submit a card for professional
grading by performing a structured **visual inspection** from guided photographs and producing
a **Predicted Grade Range** for PSA and BGS, with every contributing observation shown and
located on the card.

The app is a **pre-grading aid**, not a grading service. It never represents its output as an
official PSA, Beckett (BGS), CGC, or other professional grade. Required vocabulary:
*Estimated Grade, Pre-Grade, Predicted Grade Range, Visual Inspection, Potential Defect,
Confidence*.

Initial card games: **Pokémon** and **One Piece TCG** (both standard 63 × 88 mm).

## 2. MVP scope

Given high-quality Android photographs of a card, on-device:

1. Detect the card and its four boundaries.
2. Perspective-correct and normalize.
3. Gate image quality (blur, glare, exposure, resolution, coverage, perspective).
4. Measure centering (front and back) — or report *Unknown*.
5. Crop and inspect all 8 corners and 8 edges individually.
6. Flag likely whitening / chipping, conservatively.
7. Show located potential defects on an overlay with tap-to-zoom.
8. Produce per-category confidence and an overall confidence.
9. Combine into deterministic, explainable PSA / BGS **ranges**.
10. Save scans locally.

Surface analysis is architected (multi-lighting input, per-check capability declaration) but
only implemented where reliable; everything else is labelled *Not checked* or *Experimental*.

## 3. Explicit non-goals (MVP)

- Issuing or implying official grades, labels, or certifications.
- Claiming "Black Label" / "Pristine" from an overall score.
- Training or shipping ML models without real professionally-graded ground truth.
- Reliable detection of every surface defect (dents, roller marks, holo scratches…).
- Card identification as a prerequisite — "Unknown Card" is fully supported.
- Cloud processing, accounts, sync, marketplace/pricing features.
- iOS (the architecture keeps it possible; see §12).
- Detecting trimming, restoration, counterfeits, or other alterations.

## 4. System overview

```
┌──────────────────────────── Android app (Phase 2–6) ────────────────────────────┐
│  :app  Compose UI · Navigation · ViewModels · orchestration                      │
│    │                                                                            │
│    ├── :core:cv       pipeline contracts, deterministic geometry, quality gate   │
│    │                  (+ future :core:cv-opencv implementation module)           │
│    ├── :core:grading  GradeEstimator / ConfidenceCalculator / CandidateAssessor  │
│    ├── :core:data     Room DB, repositories (LOCAL_ONLY)                         │
│    └── :core:model    pure-Kotlin domain model shared by all modules             │
└──────────────────────────────────────────────────────────────────────────────────┘
        │  (future, explicit per-scan consent only)
        ▼
┌──────────── Future backend (design only, see backend/README.md) ────────────┐
│ FastAPI → S3 (private) → SQS → CV/ML worker → PostgreSQL → result API        │
└──────────────────────────────────────────────────────────────────────────────┘
```

## 5. Android architecture

- **Kotlin, Jetpack Compose, Material 3**, single-activity, **Navigation Compose**.
- **MVVM**: each screen has a ViewModel exposing `StateFlow` UI state; Composables are
  stateless renderers. Coroutines/Flow throughout.
- **Room** for local persistence (schema exported to `core/data/schemas/` for migration checks).
- **Manual DI** (`AppContainer`) until the graph grows (CameraX, OpenCV) — then Hilt.
- `minSdk 26`, `targetSdk/compileSdk 35`, JDK 17 toolchain.

### Modules

| Module | Type | Responsibility | Depends on |
|---|---|---|---|
| `:app` | Android app | Screens, navigation, ViewModels, demo data, orchestration | all core |
| `:core:model` | Kotlin/JVM | Domain models (Card, ScanSession, CapturedImage, ImageQualityResult, CardDefect, InspectionResult, ProfessionalGrade, PredictionComparison, grades, confidence) | — |
| `:core:cv` | Kotlin/JVM | Analyzer interfaces, `InspectionPipeline`, diagnostics model, deterministic geometry (`Quadrilateral`, `CardRegions`, `CoordinateMapper`), `ImageQualityGate` | model |
| `:core:grading` | Kotlin/JVM | `GradeEstimator`, `GradingRuleSet`, `ConfidenceCalculator`, `CandidateAssessor` | model |
| `:core:data` | Android lib | Room entities/DAOs/DB, repository interfaces + Room implementations, `LocalDataSources` | model |

Pure-JVM modules keep domain logic free of Android types → fast unit tests and a clean path
to a backend or iOS port. Room is an implementation detail of `:core:data`; `:app` only sees
repository interfaces.

### Screens (Phase 2)

Home · New Scan · Guided Capture (camera placeholder + card guide overlay + retake flow) ·
Analysis (honest "no analysis was run") · Results (DEMO/MOCK) · Saved Scans (Room-backed) ·
Settings/About · Developer Calibration (behind developer toggle).

## 6. Computer-vision pipeline

Contracts live in `:core:cv` (`analyzers/Analyzers.kt`). Each stage stamps an
`AlgorithmVersion` on its output.

```
CapturedImage
  → CardDetector            (quad outline or NotDetected)
  → ImageQualityAnalyzer    (scores) → ImageQualityGate (accept / warn / retake)
  → PerspectiveCorrector    (upright 63:88 image, known px/mm)
  → ImageNormalizer         (exposure / white balance)
  → CenteringAnalyzer       (Measured borders or Unknown)
  → CornerAnalyzer ×4 ─┐
  → EdgeAnalyzer   ×4 ─┼─ WhiteningDetector on each region
  → SurfaceAnalyzer     (straight + left/right lighting images; declares capabilities)
  → GradeEstimator + ConfidenceCalculator + CandidateAssessor
  → InspectionResult
```

- **Deterministic first.** OpenCV (contours, `approxPolyDP`, `warpPerspective`, Laplacian
  variance, specular-pixel ratio, colour-distance thresholds) where the quantity is objectively
  measurable. ML only after real labelled data exists (see ml-strategy.md).
- The OpenCV implementation will live in a separate `:core:cv-opencv` Android module so the
  contracts stay pure and testable.
- **Coordinates:** defects are stored in *normalized coordinates of the perspective-corrected
  side image* (0..1). Resolution-independent, directly drawable, mappable back to pixels via
  `CoordinateMapper`.
- **Crop geometry** is already implemented and tested: corner crops are physically square;
  edge bands exclude the corner crops so damage is not double-counted.
- **Diagnostics:** every stage can report `StageDiagnostics` to a `DiagnosticsRecorder`
  (no-op by default). The calibration screen renders them.
- **Local vs remote:** `InspectionPipeline.processingMode` distinguishes on-device
  (`LOCAL_ONLY`) from a future backend implementation (`CLOUD_ANALYSIS`) behind the same contract.

## 7. Image-quality gate

`ImageQualityResult` (per photo) carries `blurScore, glareScore, exposureScore,
resolutionScore, cardCoverageScore, perspectiveScore` (0..1, **1 = ideal**, `null` = not
measured), `acceptable`, and typed `warnings` (blocking or degrading).

- `ImageQualityGate` applies per-metric thresholds: below *block* → retake required; below
  *warn* → accepted with lowered confidence. Thresholds are **provisional placeholders** until
  calibrated in Phase 4.
- The capture flow (`CaptureFlowState`) never advances past a rejected photo.
- `ConservativeConfidenceCalculator` caps overall confidence when any photo is unacceptable,
  required captures are missing, or centering is unknown.
- A model invariant forbids `acceptable = true` with a blocking warning.

## 8. Defect representation

`CardDefect`: `id, side, category, location (region + bounding box [+ polygon]), severity,
confidence, description, sourceImageId, algorithmVersion, maturity`.

- Categories: WHITENING, EDGE_CHIP, CORNER_DAMAGE, IRREGULAR_CUT, SCRATCH, PRINT_LINE, DENT,
  STAIN, CENTERING, UNKNOWN.
- Severity: TRACE, MINOR, MODERATE, MAJOR.
- Maturity: SUPPORTED / EXPERIMENTAL / NOT_IMPLEMENTED — experimental detections are drawn
  dashed and labelled.
- Descriptions use hedged wording ("Possible whitening").
- `DefectLocation.describe()` gives "right edge, upper section" style text for the UI.

## 9. Grading result representation

`InspectionResult`: identity + card label snapshot, per-photo `imageQuality`, per-side
`centering` (Measured | Unknown), 8 `CornerResult`s, 8 `EdgeResult`s, per-side
`SurfaceResult` (with per-check capability map), all `defects`, `GradeEstimate`,
`overallConfidence`, `warnings`, `provenance` (pipeline version, algorithm versions,
processing mode, `isDemo`).

`GradeEstimate`:
- `psa`, `bgs.overall`: `RangeEstimate.Available(range, limitingFactors)` or
  `RangeEstimate.Unavailable(reason)`. Limiting factors cite defect ids → "which defects caused
  the lower bound".
- `subgrades`: CENTERING / CORNERS / EDGES / SURFACE ranges on the BGS half-point scale.
- `bgs10Candidate`, `blackLabelCandidate` (always experimental): require *every* category ≥ 10
  with high confidence and all photos acceptable. Never derived from an overall score.
- `Grade` is stored as half-points (integer) — no finer precision is representable. PSA scale
  excludes 9.5.

## 10. Persistence

Room schema v1 tables: `cards, scan_sessions, captured_images, inspection_results,
card_defects, professional_grades, prediction_comparisons`. Enums by name, times as epoch
millis, grades as half-points, algorithm/pipeline versions on every analysis row. The full
result payload column is reserved until a serialization format is chosen in Phase 4.
Demo results are rejected by `InspectionRepository.save`.

## 11. Professional-grade feedback loop

```
Scan → prediction shown → user submits card → card returns →
user records ProfessionalGrade (company, grade, BGS subgrades, cert #, dates) →
PredictionComparison (predicted range vs actual, per-subgrade outcome, pipeline version)
```

`PredictionComparison.create` refuses demo results. See dataset-strategy.md.

## 12. Future iOS considerations

- Domain, grading and geometry are pure Kotlin with no Android types. When iOS starts,
  evaluate Kotlin Multiplatform for `:core:model`, `:core:grading` and the geometry parts of
  `:core:cv` (ADR 0006 deliberately defers this).
- CV implementations are behind interfaces; the Python reference (`cv-reference/`) and
  shared synthetic fixtures define expected behaviour, so an iOS (OpenCV/Vision) or backend
  implementation can be validated against the same ground truth.
- The backend path (same `InspectionPipeline` contract) lets iOS reuse server-side analysis
  without rewriting CV.

## 13. Future backend

Design only — see [backend/README.md](../backend/README.md) and [infra/README.md](../infra/README.md).
