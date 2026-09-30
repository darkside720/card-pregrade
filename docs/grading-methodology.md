# Grading methodology

## Position

The app produces a **Predicted Grade Range** from a visual inspection of photographs. It is
not, and must never be presented as, an official grade from PSA, Beckett (BGS), CGC or any
other company.

## Status (Phase 2)

- **No grading rules are implemented.** The only estimator is `UncalibratedGradeEstimator`,
  which returns *Unavailable* with a reason.
- The results screen shows a hard-coded **DEMO / MOCK ANALYSIS** to evaluate UX.

## Principles

1. **Ranges, not points.** "PSA 9–10", never "PSA 10" as a claim and never "9.73". Grades are
   represented as half-points; PSA's scale excludes 9.5.
2. **Explainability.** Every lowered bound lists the defects that caused it
   (`GradeLimitingFactor.defectIds`).
3. **Deterministic and versioned.** A `GradingRuleSet` has an `AlgorithmVersion`; the version
   is stored with every result.
4. **Don't fabricate.** If a category cannot be measured (e.g. centering on a borderless
   card), its estimate is null and the overall range widens or becomes *Unavailable*.
5. **Confidence is conservative.** Overall confidence = the weakest component, capped when
   photos fail the quality gate, captures are missing or centering is unknown. It is *not* a
   calibrated probability until real outcome data exists.
6. **Candidates are flags, not grades.** "Potential BGS 10 candidate" and "Potential Black
   Label candidate — experimental" require all four categories estimated at 10 with high
   confidence and all photos acceptable. An overall score alone can never produce them.

## Planned approach (Phase 6)

### Centering (most objective)
Measured border ratios (L/R, T/B, front and back) compared against published company
centering tolerances. Each company's tolerance table must be sourced from its current public
grading standards, cited in the rule-set file, and reviewed when those standards change.
Front and back are evaluated separately.

### Corners, edges, surface
Map detected defect *counts × severity × confidence* to a category range. Severity thresholds
(e.g. whitening area in mm², chip depth) come from:
1. published grading-standard wording (qualitative),
2. calibration against a small set of the team's own professionally graded cards,
3. later, the prediction-vs-actual dataset.

Until (2) exists, category rules are **provisional** and the UI must say so.

### PSA overall
PSA publishes a single grade, not subgrades. The PSA range is derived from the worst
categories (a single notable defect can cap the grade) and is widened by low confidence.

### BGS overall
BGS publishes four subgrades and an overall that is not a simple mean. The rule set estimates
the four category ranges and derives the overall range with the lowest category acting as a
ceiling. The exact aggregation must be validated against real BGS labels.

### Low-quality input
- Any blocking quality warning → retake required; no estimate until resolved.
- Degrading warnings → wider ranges and capped confidence.
- Missing lighting-angle captures → surface marked *limited*.

## What would count as validation

A rule-set version is only called "calibrated" once its predictions have been compared with
real professional grades (see dataset-strategy.md) and within-range accuracy is reported per
company and per category. Until then every rule set is labelled provisional.
