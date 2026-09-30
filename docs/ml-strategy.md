# ML strategy

## Stance

ML is not added because this is an "AI" product. Deterministic geometry and image processing
come first wherever a quantity is objectively measurable (boundaries, centering, blur, glare).
ML is considered only where deterministic methods demonstrably fail **and** real labelled data
exists.

No model is trained in the MVP. No model is trained on synthetic or invented grade labels.

## Candidate uses (in likely order)

| Use | Why ML might help | Labels needed | Earliest |
|---|---|---|---|
| Card segmentation in cluttered scenes | Robust to backgrounds/sleeves | Card outlines (cheap to annotate, or from app corrections) | After Phase 4 if contour methods fail in the field |
| Defect localisation (whitening, chips) | Lighting/finish variation | Bounding boxes on crops, reviewed by humans | After a few hundred annotated scans |
| Surface anomalies (scratches, print lines, dents) using multi-angle images | Hard for rules | Annotated regions + angle stack | Later; experimental |
| Grade-range calibration | Map features → actual grade distribution | `PredictionComparison` records | Once hundreds of graded outcomes exist |
| Card identification | Catalog lookup | Public catalog metadata | Independent of grading; optional |

## Architecture hooks already in place

- Analyzer interfaces (`CornerAnalyzer`, `EdgeAnalyzer`, `WhiteningDetector`, `SurfaceAnalyzer`,
  `GradeEstimator`) let a learned implementation replace a deterministic one without UI changes.
- `AlgorithmVersion` / `AnalysisProvenance` record which model version produced each value.
- `DetectionMaturity` lets a new model ship as EXPERIMENTAL first.
- `InspectionPipeline.processingMode` allows a heavier model to run server-side (with consent).
- `ProfessionalGrade` + `PredictionComparison` provide the supervised signal.

## Evaluation requirements before shipping any model

- Held-out evaluation split by physical card and user.
- Report within-range accuracy and calibration (predicted confidence vs observed accuracy),
  per company, per category, per device class.
- Compare against the deterministic baseline; ship only if clearly better.
- Model card documenting data sources, known failure modes and version.

## On-device vs server

Prefer on-device (TFLite / LiteRT) for small detectors to keep photos local. Use server GPU
only if a model cannot run acceptably on-device, and only under the cloud consent rules.
