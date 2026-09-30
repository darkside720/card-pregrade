# 0004 — Deterministic CV before ML

## Context
Many inspection quantities are geometric and directly measurable: card outline, perspective, border widths, blur and glare. No real labelled grading dataset exists yet.

## Decision
Use OpenCV and deterministic image processing wherever a quantity is objectively measurable. Do not introduce ML for its own sake, and never train on synthetic or invented grade labels. Every analyzer is behind an interface so that a learned implementation can replace it later.

## Consequences
- Results are explainable, reproducible and testable against synthetic ground truth.
- Surface defects will lag behind (hard to do deterministically) and are labelled experimental or not checked.
- ML is revisited once `PredictionComparison` data exists (docs/ml-strategy.md).
