# 0007 — Defect coordinates normalized to the perspective-corrected side image

## Context
Defects must be drawn on the card, zoomed into, compared across devices and resolutions, and stored for datasets.

## Decision
`DefectLocation` uses `NormalizedRect` / `NormalizedPoint` in 0..1 of the **perspective-corrected image of that side** (upright, 63:88). `CoordinateMapper` converts to and from pixels, snapping floating-point noise to exact pixel boundaries. `CardDefect.sourceImageId` records which photo the evidence came from.

## Consequences
- Overlay and zoom code is resolution-independent.
- Mapping back to the *original* photo requires the stored homography, which will be saved with the corrected image in Phase 4.
