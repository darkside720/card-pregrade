package com.cardpregrade.core.grading

import com.cardpregrade.core.model.AnalysisStatus
import com.cardpregrade.core.model.CenteringResult
import com.cardpregrade.core.model.Confidence

data class ConfidenceAssessment(
    val overall: Confidence,
    /** Why confidence was capped, for display in the result warnings. */
    val limitingReasons: List<String>,
)

interface ConfidenceCalculator {
    fun calculate(input: GradingInput): ConfidenceAssessment
}

/**
 * Caps applied when inputs are degraded. Values are policy knobs, not statistical results.
 */
data class ConfidencePolicy(
    val lowQualityImageCap: Double,
    val missingCaptureCap: Double,
    val centeringUnknownCap: Double,
) {
    companion object {
        /** UNCALIBRATED placeholders; revisit once real prediction-vs-actual data exists. */
        val PROVISIONAL = ConfidencePolicy(
            lowQualityImageCap = 0.4,
            missingCaptureCap = 0.5,
            centeringUnknownCap = 0.6,
        )
    }
}

/**
 * Conservative combination: overall confidence is the *minimum* of the component
 * confidences (a chain is as strong as its weakest link), further capped when images are
 * unacceptable, captures are missing, or centering could not be measured. With no analysed
 * components at all, confidence is zero.
 */
class ConservativeConfidenceCalculator(private val policy: ConfidencePolicy) : ConfidenceCalculator {

    override fun calculate(input: GradingInput): ConfidenceAssessment {
        val components = buildList {
            input.centering.filterIsInstance<CenteringResult.Measured>().forEach { add(it.confidence) }
            input.corners.filter { it.status == AnalysisStatus.ANALYZED }.forEach { add(it.confidence) }
            input.edges.filter { it.status == AnalysisStatus.ANALYZED }.forEach { add(it.confidence) }
            input.surface.filter { it.status == AnalysisStatus.ANALYZED }.forEach { add(it.confidence) }
        }
        if (components.isEmpty()) {
            return ConfidenceAssessment(Confidence.NONE, listOf("No analysis results are available."))
        }

        var value = components.minOf { it.value }
        val reasons = mutableListOf<String>()

        fun cap(limit: Double, reason: String) {
            if (value > limit) value = limit
            reasons += reason
        }

        if (input.imageQuality.any { !it.acceptable }) {
            cap(policy.lowQualityImageCap, "One or more photographs did not pass the quality check.")
        }
        if (!input.allRequiredCapturesPresent) {
            cap(policy.missingCaptureCap, "Not all required photographs were captured.")
        }
        if (input.centering.any { it is CenteringResult.Unknown }) {
            cap(policy.centeringUnknownCap, "Centering could not be measured for every side.")
        }
        return ConfidenceAssessment(Confidence(value), reasons)
    }
}
