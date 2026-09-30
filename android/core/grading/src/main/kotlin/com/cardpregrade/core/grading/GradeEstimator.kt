package com.cardpregrade.core.grading

import com.cardpregrade.core.model.AlgorithmVersion
import com.cardpregrade.core.model.BgsEstimate
import com.cardpregrade.core.model.CandidateIndicator
import com.cardpregrade.core.model.CandidateStatus
import com.cardpregrade.core.model.CardDefect
import com.cardpregrade.core.model.CenteringResult
import com.cardpregrade.core.model.CornerResult
import com.cardpregrade.core.model.EdgeResult
import com.cardpregrade.core.model.GradeEstimate
import com.cardpregrade.core.model.ImageQualityResult
import com.cardpregrade.core.model.RangeEstimate
import com.cardpregrade.core.model.SubgradeCategory
import com.cardpregrade.core.model.SubgradeEstimate
import com.cardpregrade.core.model.SurfaceResult

/** Everything a grade estimator may use. Card identity is deliberately absent. */
data class GradingInput(
    val imageQuality: List<ImageQualityResult>,
    val centering: List<CenteringResult>,
    val corners: List<CornerResult>,
    val edges: List<EdgeResult>,
    val surface: List<SurfaceResult>,
    val defects: List<CardDefect>,
    /** True when every capture the protocol requires is present. */
    val allRequiredCapturesPresent: Boolean,
)

/**
 * Maps visual findings to predicted PSA/BGS ranges.
 *
 * Implementations must be deterministic, versioned, and explainable (every lowered bound
 * cites the defects responsible). They must return [RangeEstimate.Unavailable] rather than
 * a guess when inputs are insufficient.
 */
interface GradeEstimator {
    val ruleSet: AlgorithmVersion
    fun estimate(input: GradingInput): GradeEstimate
}

/**
 * A versioned mapping from measured defects to category estimates. Intentionally just a
 * contract in Phase 2: rules will be written from published grading standards and then
 * validated against real professionally graded cards (docs/grading-methodology.md).
 */
interface GradingRuleSet {
    val version: AlgorithmVersion
    fun subgrade(category: SubgradeCategory, input: GradingInput): SubgradeEstimate
}

/**
 * The only estimator shipped in Phase 2. No calibrated rule set exists yet, so it states that
 * plainly instead of producing numbers.
 */
class UncalibratedGradeEstimator : GradeEstimator {
    override val ruleSet = AlgorithmVersion("uncalibrated", "0")

    override fun estimate(input: GradingInput): GradeEstimate {
        val reason = "No calibrated grading rule set is available yet."
        val notEvaluated = CandidateIndicator(CandidateStatus.NOT_EVALUATED, experimental = false, reasons = listOf(reason))
        return GradeEstimate(
            psa = RangeEstimate.Unavailable(reason),
            bgs = BgsEstimate(
                overall = RangeEstimate.Unavailable(reason),
                bgs10Candidate = notEvaluated,
                blackLabelCandidate = notEvaluated.copy(experimental = true),
            ),
            subgrades = emptyList(),
            ruleSetVersion = ruleSet,
        )
    }
}
