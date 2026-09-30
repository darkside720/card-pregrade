# Dataset strategy

## Goal

Build a dataset of **photos → app prediction → actual professional result** so rule sets can
be calibrated and, later, models can be trained on real labels.

## What is captured

**Before grading (at scan time)**
- Raw photos for each capture step (front/back straight, lighting angles; later macros)
- Device metadata: manufacturer, model, OS, app version, camera id, focal length, ISO,
  exposure time, flash (`DeviceMetadata`)
- Lighting metadata (`LightingMetadata`)
- Image-quality results per photo
- App-predicted defects (with coordinates, severity, confidence, algorithm version)
- App-predicted PSA/BGS ranges, subgrades, confidence, rule-set and pipeline versions

**After professional grading (user-entered, `ProfessionalGrade`)**
- Grading company, overall grade, qualifier (e.g. Pristine / Black Label)
- BGS subgrades where available (centering, corners, edges, surface)
- Certification number, submission date, graded date

**Comparison (`PredictionComparison`)**
- Predicted range vs actual, per-subgrade outcome, pipeline version, comparison time

## Rules

- **Real labels only.** No synthetic or invented grade labels are ever used to train or
  calibrate grade prediction. Synthetic data is only for geometric/CV unit tests.
- **Demo data is excluded** by construction (`isDemo` provenance; repositories and
  `PredictionComparison.create` refuse it).
- **Consent-gated.** Nothing leaves the device without explicit dataset-contribution consent
  (see privacy.md). Local records are still useful for the user's own history.
- **Reproducibility.** Every prediction carries algorithm and pipeline versions, so accuracy
  can be reported per version and old predictions are never silently reinterpreted.
- **Leakage control.** Split by *physical card* (and by user), never by photo, so the same
  card never appears in both training and evaluation.
- **Bias awareness.** Track the distribution of games, sets, eras, finishes (holo / non-holo),
  devices and grade levels. High-grade cards dominate submissions; low grades will be scarce.

## Bootstrapping

1. The team's own cards with known PSA/BGS results (photographed with the app).
2. Cards scanned before submission and recorded after return (true prospective data).
3. Optional, consented community contributions later.

Photos of already-slabbed cards are a different domain (plastic glare, no edge access) and are
kept separate if collected at all.
