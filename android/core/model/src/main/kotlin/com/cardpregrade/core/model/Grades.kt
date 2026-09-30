package com.cardpregrade.core.model

/**
 * A grade on a half-point scale, stored as an integer count of half points to avoid
 * floating-point artefacts. The app never reports finer precision than half points
 * (no "9.73").
 */
@JvmInline
value class Grade private constructor(val halfPoints: Int) : Comparable<Grade> {
    val value: Double get() = halfPoints / 2.0

    override fun compareTo(other: Grade): Int = halfPoints.compareTo(other.halfPoints)

    fun format(): String = if (halfPoints % 2 == 0) (halfPoints / 2).toString() else value.toString()

    companion object {
        fun of(value: Double): Grade {
            val half = value * 2
            require(half == kotlin.math.floor(half)) { "Grades use half-point steps, got $value" }
            require(value in 1.0..10.0) { "Grade out of range: $value" }
            return Grade(half.toInt())
        }

        fun of(value: Int): Grade = of(value.toDouble())
    }
}

enum class GradingCompany(val displayName: String) {
    PSA("PSA"),
    BGS("BGS"),
    CGC("CGC"),
    OTHER("Other"),
}

/** The set of grade values a company actually issues. */
enum class GradeScale {
    /** PSA: 1–10, with half grades from 1.5 to 8.5 (no 9.5). */
    PSA,

    /** BGS: 1–10 in half-point steps (overall and subgrades). */
    BGS,
    ;

    fun contains(grade: Grade): Boolean = when (this) {
        PSA -> grade.value <= 8.5 || grade.halfPoints % 2 == 0
        BGS -> true
    }

    val allGrades: List<Grade>
        get() = (2..20).map { Grade.of(it / 2.0) }.filter { contains(it) }
}

/** An inclusive predicted range such as PSA 9–10. */
data class GradeRange(val scale: GradeScale, val low: Grade, val high: Grade) {
    init {
        require(scale.contains(low) && scale.contains(high)) { "Grade not on $scale scale: $low..$high" }
        require(low <= high) { "Range low must not exceed high" }
    }

    operator fun contains(grade: Grade): Boolean = grade in low..high

    /** "9–10" (en dash) or "10" when the range is a single value. */
    fun format(): String = if (low == high) low.format() else "${low.format()}–${high.format()}"

    companion object {
        fun single(scale: GradeScale, grade: Grade) = GradeRange(scale, grade, grade)
    }
}

enum class SubgradeCategory { CENTERING, CORNERS, EDGES, SURFACE }

/** Visual estimate for one BGS-style category. */
data class SubgradeEstimate(
    val category: SubgradeCategory,
    /** Null when the category could not be estimated (e.g. centering unknown). */
    val range: GradeRange?,
    val confidence: Confidence,
    /** Defects that pulled this estimate down, for explainability. */
    val limitingDefectIds: List<String> = emptyList(),
)

/** A reason the low end of a range is where it is. */
data class GradeLimitingFactor(
    val description: String,
    val defectIds: List<String> = emptyList(),
)

/** Predicted range or an explicit statement that no prediction is possible. */
sealed interface RangeEstimate {
    data class Available(
        val range: GradeRange,
        val limitingFactors: List<GradeLimitingFactor>,
    ) : RangeEstimate

    data class Unavailable(val reason: String) : RangeEstimate
}

enum class CandidateStatus {
    NOT_EVALUATED,
    NOT_INDICATED,
    POTENTIAL_CANDIDATE,
}

/**
 * Flags such as "Potential BGS 10 candidate". Never a claim of an actual grade/label.
 * Black Label indication is always [experimental].
 */
data class CandidateIndicator(
    val status: CandidateStatus,
    val experimental: Boolean,
    val reasons: List<String>,
)

data class BgsEstimate(
    val overall: RangeEstimate,
    val bgs10Candidate: CandidateIndicator,
    val blackLabelCandidate: CandidateIndicator,
)

data class GradeEstimate(
    val psa: RangeEstimate,
    val bgs: BgsEstimate,
    val subgrades: List<SubgradeEstimate>,
    /** Identifies the rule set that produced the estimate, for reproducibility. */
    val ruleSetVersion: AlgorithmVersion,
)
