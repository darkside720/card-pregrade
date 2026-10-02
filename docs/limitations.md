# Known limitations

## Fundamental (photographic inspection)

- Photos cannot fully reveal surface defects that depend on viewing angle, magnification or
  touch: light scratches, dents, indentations, roller marks, holo scratches, micro-chipping.
- Card thickness, warping and edge profile are not observable from a straight photo.
- Trimming, restoration, cleaning, pressing and counterfeits are out of scope.
- Colour and whiteness depend on lighting, white balance and the phone's image processing;
  "whitening" detection is sensitive to both.
- Professional graders inspect the physical card under controlled light and magnification. They
  may find defects the app cannot see, and they apply judgement that no rule set fully captures.
- Grading standards and grader behaviour change over time; historical accuracy does not
  guarantee future accuracy.

## Current build (Phase 3)

Implemented: CameraX guided four-photo capture, local storage and Room persistence, and
technical capture-quality checks (see [roadmap.md](roadmap.md#phase-3--camera-mvp-)).

Not implemented:
- **No card-condition computer vision runs.** Card detection, perspective correction,
  pixel-derived centering, corner, edge, whitening, scratch, print-line and other surface
  analysis are interfaces or explicit *Not implemented* placeholders. No production inspection
  pipeline exists.
- **No ML.** The app has no ML dependency or model files and performs no inference.
- **No grading.** The only estimator returns *Unavailable*. There is no calibrated PSA or BGS
  prediction, no photo-derived subgrade or defect confidence, no continuous condition score
  (e.g. 9.7 / 10), and no probability of receiving any particular grade.
- **Results are DEMO / MOCK.** Every value on the Results screen (grade ranges, subgrades,
  confidence, defects, limiting factors, candidate indicators) is fixed sample data used to
  evaluate the layout. It is not derived from captured photographs. The screen says so with a
  banner and a persistent **DEMO** badge in the top bar.
- **Capture quality is not card condition.** The capture-quality checks only decide whether a
  photo is usable for the capture workflow (blocking only undecodable or far-too-low-resolution
  photos; other issues need a recorded override). They do not evaluate the card and do not
  affect any PSA/BGS estimate, centering, corners, edges, surface, defects or confidence.
- Capture-quality and confidence thresholds are **uncalibrated placeholders**.
- Developer mode is in-memory and resets when the app restarts.
- Full inspection results are not yet persisted (only a summary schema exists).
- Dependency versions are deliberately conservative (AGP 8.7.3, Compose BOM 2024.12.01); lint
  reports newer versions, and upgrading is planned as a separate, tested change.
- The adaptive launcher icon stays in `mipmap-anydpi-v26` (lint's `ObsoleteSdkInt` is suppressed)
  because moving it breaks resource linking with AGP 8.7.3.

## Card coverage

- Only standard-size (63 × 88 mm) cards. Japanese mini-size and oversized cards need a
  different geometry profile.
- Full-art and borderless designs have no printed border, so centering may be *Unknown* by design.
- Sleeved or top-loaded cards will reduce accuracy (glare, edge occlusion); the capture flow
  should ask for a raw card.
