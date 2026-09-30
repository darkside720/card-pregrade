package com.cardpregrade.core.model

import java.util.Locale

/**
 * A centering split as percentages that sum to 100, e.g. 51.2 / 48.8.
 * [first] is left (for L/R) or top (for T/B).
 */
data class CenteringRatio(val first: Double, val second: Double) {
    init {
        require(first in 0.0..100.0 && second in 0.0..100.0) { "Ratio out of range: $first/$second" }
        require(kotlin.math.abs(first + second - 100.0) < 1e-6) { "Ratio must sum to 100: $first/$second" }
    }

    /** The larger share, e.g. 55 for 55/45. Graders typically reason about this value. */
    val larger: Double get() = maxOf(first, second)

    fun format(): String = String.format(Locale.ROOT, "%.1f / %.1f", first, second)

    companion object {
        /** Builds a split from two opposing border widths measured in the same units. */
        fun fromBorders(firstBorder: Double, secondBorder: Double): CenteringRatio {
            require(firstBorder >= 0 && secondBorder >= 0) { "Border widths must be non-negative" }
            val total = firstBorder + secondBorder
            require(total > 0) { "At least one border must have non-zero width" }
            val first = firstBorder / total * 100.0
            return CenteringRatio(first, 100.0 - first)
        }
    }
}

/** Raw border widths in pixels of the perspective-corrected image. */
data class BorderWidths(val left: Double, val right: Double, val top: Double, val bottom: Double)

enum class CenteringUnavailableReason {
    NOT_IMPLEMENTED,
    CARD_NOT_DETECTED,
    BORDERS_NOT_DETECTED,
    /** e.g. full-art / borderless cards where printed borders do not exist. */
    BORDERLESS_DESIGN,
    IMAGE_QUALITY_INSUFFICIENT,
}

/**
 * Centering for one side. The pipeline returns [Unknown] rather than fabricating numbers
 * when borders cannot be located reliably.
 */
sealed interface CenteringResult {
    val side: CardSide

    data class Measured(
        override val side: CardSide,
        val borders: BorderWidths,
        val confidence: Confidence,
        val sourceImageId: String,
        val algorithmVersion: AlgorithmVersion,
    ) : CenteringResult {
        val leftRight: CenteringRatio get() = CenteringRatio.fromBorders(borders.left, borders.right)
        val topBottom: CenteringRatio get() = CenteringRatio.fromBorders(borders.top, borders.bottom)
        val isLowConfidence: Boolean get() = confidence.level == ConfidenceLevel.LOW
    }

    data class Unknown(
        override val side: CardSide,
        val reason: CenteringUnavailableReason,
        val detail: String? = null,
    ) : CenteringResult
}
