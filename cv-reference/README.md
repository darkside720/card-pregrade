# cv-reference (optional)

Python/OpenCV **reference** implementations of the deterministic CV stages, plus the synthetic
fixture generator. See ADR 0009.

- **Not required** to build or run the Android app.
- Used to prototype and visualise algorithms, generate synthetic fixtures with exact ground
  truth, and check parity with the Kotlin implementations.
- Future home of the backend worker's CV core (see ../backend/README.md).

## Status (Phase 2)

Skeleton only. `cv_reference/synthetic.py` defines the fixture spec (mirroring Kotlin
`SyntheticCardSpec`) and a renderer. The dependencies are **not installed** on the dev machine
yet, so the renderer has not been executed. Only the syntax has been checked.

## Setup (project-local, never global)

```bash
cd card-pregrade
python3 -m venv .venv
.venv/bin/pip install -r cv-reference/requirements.txt
(cd cv-reference && ../.venv/bin/python -m pytest)
```

## Planned layout

```
cv_reference/
  synthetic.py      fixture specs + rendering (card, art box, warps, blur, glare, defects)
  detect.py         card outline detection (Phase 4)
  correct.py        perspective correction (Phase 4)
  quality.py        blur / glare / exposure metrics (Phase 4)
  centering.py      border measurement (Phase 4)
  regions.py        corner/edge crop geometry (mirrors Kotlin CardRegions)
tests/
```
