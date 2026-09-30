# test-data

Fixtures for CV tests and calibration.

| Folder | Contents | Tracked in git? |
|---|---|---|
| `synthetic/` | Generated images/specs with exact ground truth (Kotlin `SyntheticCardSpec`, Python `cv_reference.synthetic`) | Yes, generated PNGs may be committed |
| `known-good/` | Real photos of cards believed to be clean, with metadata | **No** by default (images are git-ignored) |
| `known-defects/` | Real photos with manually annotated defects (JSON sidecars) | **No** by default |

## Rules

- **No copyrighted card-image datasets** (scraped scans, marketplace photos, official artwork)
  without written permission.
- Real photos may only be committed with the owner's permission **and** a license note in a
  sidecar `*.json` (`owner`, `license`, `device`, `lighting`, `annotations`, `professional_grade` if known).
- Strip EXIF location data before sharing any real photo.
- Synthetic fixtures are for **geometry/image-processing tests only**. They are never used as
  grade labels.
- Prefer small, purpose-built fixtures (one property per image) over large galleries.

## Sidecar format (proposed)

```json
{
  "id": "kd-0001",
  "side": "BACK",
  "owner": "project team",
  "license": "internal-test-only",
  "device": {"manufacturer": "Google", "model": "Pixel 9"},
  "lighting": "diffuse overhead",
  "annotations": [
    {"category": "WHITENING", "region": "EDGE_RIGHT", "box": [0.955, 0.12, 1.0, 0.26], "severity": "MINOR"}
  ],
  "professional_grade": null
}
```
