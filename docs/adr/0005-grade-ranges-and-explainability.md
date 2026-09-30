# 0005 — Grade ranges on a half-point scale, with explainable limiting factors

## Context
Point grades from photos would imply false precision and could be mistaken for official grades.

## Decision
- `Grade` stores half-points as an integer, so no finer precision can be represented. `GradeScale.PSA` excludes 9.5.
- Predictions are `GradeRange`s ("9–10") or an explicit `Unavailable(reason)`.
- Each range lists `GradeLimitingFactor`s referencing defect ids.
- BGS 10 and Black Label candidate indicators require every category at 10 with high confidence and all photos acceptable. Black Label is always experimental. Neither can come from an overall score.
- Until a calibrated rule set exists, the only estimator returns `Unavailable`.

## Consequences
- The UI can always answer "why not higher?".
- The PSA and BGS rule sets are separate, versioned artefacts.
