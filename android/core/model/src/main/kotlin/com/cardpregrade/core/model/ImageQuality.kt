package com.cardpregrade.core.model

/**
 * Output of the capture-quality check for a single photograph.
 *
 * These are **capture quality** measurements (is this photo usable?), never card defects.
 *
 * Every score is normalized to 0..1 where **1.0 is ideal and 0.0 is unusable** (so a high
 * [blurScore] means *sharp*). A null score means "not measured" — never "perfect".
 * The per-metric [checks] (with their verdicts) are authoritative; scores are convenience values.
 *
 * Policy (Phase 3): thresholds are uncalibrated, so soft problems produce WARNING /
 * RETAKE_RECOMMENDED verdicts that the user may override. Only technically unusable input
 * (decode failure, catastrophically low resolution) is a blocking warning and makes
 * [acceptable] false.
 */
data class ImageQualityResult(
    val sourceImageId: String,
    val blurScore: Double?,
    val glareScore: Double?,
    val exposureScore: Double?,
    val resolutionScore: Double?,
    val cardCoverageScore: Double?,
    val perspectiveScore: Double?,
    val acceptable: Boolean,
    val warnings: List<ImageQualityWarning>,
    val algorithmVersion: AlgorithmVersion,
    val checks: List<QualityCheck> = emptyList(),
) {
    init {
        listOf(blurScore, glareScore, exposureScore, resolutionScore, cardCoverageScore, perspectiveScore)
            .filterNotNull()
            .forEach { require(it in 0.0..1.0) { "Quality score out of range: $it" } }
        require(!acceptable || warnings.none { it.blocking }) {
            "An image with blocking warnings cannot be acceptable"
        }
    }

    val hasBlockingIssue: Boolean get() = warnings.any { it.blocking }

    /** Worst verdict across checks; UNUSABLE whenever the image is not acceptable. */
    val verdict: QualityVerdict
        get() = when {
            !acceptable -> QualityVerdict.UNUSABLE
            checks.isEmpty() -> QualityVerdict.GOOD
            else -> checks.maxOf { it.verdict }
        }

    companion object {
        /** Builds a result whose warnings and acceptability are derived from [checks]. */
        fun fromChecks(
            sourceImageId: String,
            checks: List<QualityCheck>,
            algorithmVersion: AlgorithmVersion,
            blurScore: Double? = null,
            exposureScore: Double? = null,
            resolutionScore: Double? = null,
        ): ImageQualityResult {
            val warnings = checks.filter { it.verdict != QualityVerdict.GOOD }.map { check ->
                ImageQualityWarning(
                    type = check.issue(),
                    blocking = check.verdict == QualityVerdict.UNUSABLE,
                    detail = check.message,
                )
            }
            return ImageQualityResult(
                sourceImageId = sourceImageId,
                blurScore = blurScore,
                glareScore = null,
                exposureScore = exposureScore,
                resolutionScore = resolutionScore,
                cardCoverageScore = null,
                perspectiveScore = null,
                acceptable = warnings.none { it.blocking },
                warnings = warnings,
                algorithmVersion = algorithmVersion,
                checks = checks,
            )
        }

        private fun QualityCheck.issue(): ImageQualityIssue = when (metric) {
            QualityMetric.DECODE -> ImageQualityIssue.UNDECODABLE
            QualityMetric.RESOLUTION -> ImageQualityIssue.INSUFFICIENT_RESOLUTION
            QualityMetric.SHARPNESS -> ImageQualityIssue.BLUR
            QualityMetric.EXPOSURE ->
                if ((measuredValue ?: 0.0) < MID_GREY) ImageQualityIssue.UNDEREXPOSED else ImageQualityIssue.OVEREXPOSED
            QualityMetric.HIGHLIGHT_CLIPPING -> ImageQualityIssue.GLARE
        }

        private const val MID_GREY = 128.0
    }
}

/** Ordered from best to worst so that `max` gives the overall verdict. */
enum class QualityVerdict { GOOD, WARNING, RETAKE_RECOMMENDED, UNUSABLE }

enum class QualityMetric { DECODE, RESOLUTION, SHARPNESS, EXPOSURE, HIGHLIGHT_CLIPPING }

/** One capture-quality measurement with its verdict, e.g. SHARPNESS = 412.7 (Laplacian variance) → GOOD. */
data class QualityCheck(
    val metric: QualityMetric,
    val verdict: QualityVerdict,
    /** Raw measured value; null when it could not be measured. */
    val measuredValue: Double?,
    val unit: String?,
    val message: String,
)

/**
 * A detected image problem. [blocking] warnings mean the photo cannot be used at all;
 * non-blocking ones are recommendations the user may override.
 */
data class ImageQualityWarning(
    val type: ImageQualityIssue,
    val blocking: Boolean,
    val detail: String? = null,
)

enum class ImageQualityIssue {
    UNDECODABLE,
    CARD_NOT_DETECTED,
    CARD_OUTSIDE_FRAME,
    CARD_PARTIALLY_OBSCURED,
    CARD_TOO_FAR,
    EXCESSIVE_PERSPECTIVE,
    BLUR,
    GLARE,
    UNDEREXPOSED,
    OVEREXPOSED,
    INSUFFICIENT_RESOLUTION,
}
