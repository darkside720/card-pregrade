# Testing strategy

## Layers

| Layer | Where | Runs on | Current |
|---|---|---|---|
| Domain invariants | `core/model/src/test` | JVM | Centering ratios, grade scales/ranges, defect locations, quality invariants, comparisons |
| Deterministic CV geometry | `core/cv/src/test` | JVM | Quad ordering/area/convexity, corner/edge crop geometry, coordinate mapping, quality gate, placeholder analyzers, synthetic fixture generator |
| Grading policy | `core/grading/src/test` | JVM | Conservative confidence, candidate rules, uncalibrated estimator refuses to grade |
| Persistence mapping | `core/data/src/test` | JVM | Entity ↔ domain round trips, summary formatting |
| App logic | `app/src/test` | JVM | Capture state machine (reject/retake/advance), demo-result invariants |
| UI / navigation | `app/src/androidTest` | Emulator/device | Compose navigation smoke test across all screens, demo banner, disclaimer |
| CV algorithms (Phase 4+) | `core/cv-opencv` + `cv-reference` | JVM/Android + Python | Detector, corrector, centering, blur, glare vs synthetic ground truth |

## Commands

```bash
cd android
./gradlew test                              # all JVM unit tests
./gradlew :app:connectedDebugAndroidTest    # instrumented (needs emulator/device)
./gradlew :app:lintDebug :core:data:lintDebug
```

## Synthetic fixture strategy

Geometric correctness is tested against **generated** images with exactly known ground truth,
never against copyrighted card scans.

- `SyntheticCardSpec` / `SyntheticCard` (Kotlin, `core/cv/src/test/.../fixtures`) render a
  canvas with a card rectangle and an inner art box at known pixel coordinates. The spec
  exposes the true border widths and centering.
- Phase 4 extends this with: perspective warps (known homography), rotation, Gaussian blur
  (known sigma), synthetic specular highlights (known area), exposure shifts, background clutter,
  partial occlusion, and synthetic whitening/chips painted at known coordinates.
- `cv-reference/` generates the same specs in Python so both implementations are checked
  against identical ground truth.
- Tests assert tolerances (e.g. detected corners within 2 px, centering within 0.5 percentage
  points) rather than exact equality once real image processing is involved.

## Principles

- Every CV function gets a synthetic test before it is used in grading.
- Tests encode *refusal* behaviour too (e.g. centering returns Unknown on a borderless fixture).
- Tuning thresholds on real photos happens in calibration mode; resulting constants are
  checked in with the fixture that justified them.
- Never make up test results: CI output is the source of truth.
