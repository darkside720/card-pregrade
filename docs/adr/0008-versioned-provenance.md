# 0008 — Algorithm/pipeline versions on every result

## Context
Algorithms will change. Historical predictions must remain interpretable, and accuracy must be measurable per version.

## Decision
Every analyzer exposes an `AlgorithmVersion`. Results carry `AnalysisProvenance` (pipeline version, algorithm list, processing mode, `isDemo`). Defects, quality results and rule sets carry their own versions, and Room rows store them. `isDemo` results are refused by persistence and by `PredictionComparison.create`.

## Consequences
- The dataset can be sliced by version, and old results are never silently recomputed.
- Version strings must be bumped deliberately whenever behaviour changes.
