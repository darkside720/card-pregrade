# Testing strategy

## Layers

| Layer | Where | Runs on | Current |
|---|---|---|---|
| Domain invariants | `core/model/src/test` | JVM | Centering ratios, grade scales/ranges, defect locations, quality invariants, comparisons |
| Deterministic CV geometry | `core/cv/src/test` | JVM | Quad ordering/area/convexity, corner/edge crop geometry, coordinate mapping, capture-quality analyzer, placeholder analyzers, synthetic fixture generator |
| Grading policy | `core/grading/src/test` | JVM | Conservative confidence, candidate rules, uncalibrated estimator refuses to grade |
| Persistence mapping | `core/data/src/test` | JVM | Entity ↔ domain round trips, summary formatting |
| App logic | `app/src/test` | JVM | Capture state machine (review/accept/override/retake/resume), capture ViewModel, capture storage, demo-result invariants |
| UI / navigation | `app/src/androidTest` | Emulator/device | Navigation smoke test across all screens, DEMO banner and persistent DEMO badge, demo defect dialog wording; guided capture with a fake camera (permissions, quality verdicts, override, retake, persistence, orientation, session isolation and resume) |
| CV algorithms (Phase 4+) | `core/cv-opencv` + `cv-reference` | JVM/Android + Python | Detector, corrector, centering, blur, glare vs synthetic ground truth |

## Commands

```bash
cd android
./gradlew test                              # all JVM unit tests
./gradlew :app:connectedDebugAndroidTest    # instrumented (needs emulator/device)
./gradlew :app:lintDebug :core:data:lintDebug
```

## Current status (Phase 3)

- JVM unit tests, `lintDebug` and `assembleDebug` pass.
- The instrumented suite (19 tests) passes on a Google Pixel 8 Pro running Android 17 / API 37.
  Android 17 needs Espresso 3.7.0 or newer; the transitive 3.5.0 from Compose UI test fails
  there before any assertion runs.
- Persistent DEMO labelling was checked by hand while scrolling the entire Results screen.
- Instrumented tests run against the installed debug app and leave test sessions in its local
  storage and database. Back up or clear app data on a device that also holds real captures.

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
