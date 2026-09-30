package com.cardpregrade.core.model

import java.time.Instant

enum class ComparisonOutcome {
    WITHIN_RANGE,
    ACTUAL_ABOVE_RANGE,
    ACTUAL_BELOW_RANGE,
    NO_PREDICTION,
    NO_ACTUAL,
}

data class SubgradeComparison(
    val category: SubgradeCategory,
    val predicted: GradeRange?,
    val actual: Grade?,
    val outcome: ComparisonOutcome,
)

/** Prediction vs. professional result, recorded with the versions that made the prediction. */
data class PredictionComparison(
    val id: String,
    val inspectionResultId: String,
    val professionalGradeId: String,
    val company: GradingCompany,
    val predicted: GradeRange?,
    val actual: Grade,
    val outcome: ComparisonOutcome,
    val subgrades: List<SubgradeComparison>,
    val pipelineVersion: String,
    val comparedAt: Instant,
) {
    companion object {
        fun outcome(predicted: GradeRange?, actual: Grade?): ComparisonOutcome = when {
            predicted == null -> ComparisonOutcome.NO_PREDICTION
            actual == null -> ComparisonOutcome.NO_ACTUAL
            actual in predicted -> ComparisonOutcome.WITHIN_RANGE
            actual > predicted.high -> ComparisonOutcome.ACTUAL_ABOVE_RANGE
            else -> ComparisonOutcome.ACTUAL_BELOW_RANGE
        }

        fun create(
            id: String,
            inspection: InspectionResult,
            actual: ProfessionalGrade,
            comparedAt: Instant,
        ): PredictionComparison {
            require(!inspection.provenance.isDemo) { "Demo results must never enter the feedback dataset" }
            val predicted = when (actual.company) {
                GradingCompany.PSA -> inspection.estimate.psa
                GradingCompany.BGS -> inspection.estimate.bgs.overall
                else -> null
            }.let { (it as? RangeEstimate.Available)?.range }

            val subgrades = if (actual.company == GradingCompany.BGS && actual.bgsSubgrades != null) {
                SubgradeCategory.entries.map { category ->
                    val predictedSub = inspection.subgrade(category)?.range
                    val actualSub = actual.bgsSubgrades.get(category)
                    SubgradeComparison(category, predictedSub, actualSub, outcome(predictedSub, actualSub))
                }
            } else {
                emptyList()
            }

            return PredictionComparison(
                id = id,
                inspectionResultId = inspection.id,
                professionalGradeId = actual.id,
                company = actual.company,
                predicted = predicted,
                actual = actual.overall,
                outcome = outcome(predicted, actual.overall),
                subgrades = subgrades,
                pipelineVersion = inspection.provenance.pipelineVersion,
                comparedAt = comparedAt,
            )
        }
    }
}
