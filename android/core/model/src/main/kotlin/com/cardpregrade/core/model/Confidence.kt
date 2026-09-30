package com.cardpregrade.core.model

import kotlin.math.roundToInt

/**
 * A confidence value in 0..1.
 *
 * This expresses how much the pipeline trusts its own measurement given input quality and
 * algorithm maturity. It is NOT a calibrated probability that a professional grader agrees;
 * that calibration requires real graded-card data (see docs/ml-strategy.md).
 */
@JvmInline
value class Confidence(val value: Double) : Comparable<Confidence> {
    init {
        require(value in 0.0..1.0) { "Confidence must be in 0..1, was $value" }
    }

    val percent: Int get() = (value * 100).roundToInt()

    /**
     * Presentation bucket only. Thresholds are provisional UI conventions, not statistical
     * claims, and live here so every screen buckets identically.
     */
    val level: ConfidenceLevel
        get() = when {
            value >= HIGH_THRESHOLD -> ConfidenceLevel.HIGH
            value >= MEDIUM_THRESHOLD -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }

    override fun compareTo(other: Confidence): Int = value.compareTo(other.value)

    companion object {
        const val HIGH_THRESHOLD = 0.8
        const val MEDIUM_THRESHOLD = 0.5
        val NONE = Confidence(0.0)
    }
}

enum class ConfidenceLevel { LOW, MEDIUM, HIGH }
