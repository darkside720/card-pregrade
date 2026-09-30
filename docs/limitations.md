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

## Current build (Phase 2)

- **No camera capture.** The guided capture screen uses placeholder captures.
- **No computer vision runs.** Card detection, correction, centering, corner, edge, whitening
  and surface analysis are interfaces or explicit *Not implemented* placeholders.
- **No grading rules.** The only estimator returns *Unavailable*.
- The results screen shows **DEMO / MOCK** data only.
- Quality-gate and confidence thresholds are **uncalibrated placeholders**.
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
