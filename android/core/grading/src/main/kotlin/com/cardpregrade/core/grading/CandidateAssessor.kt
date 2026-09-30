package com.cardpregrade.core.grading

import com.cardpregrade.core.model.CandidateIndicator
import com.cardpregrade.core.model.CandidateStatus
import com.cardpregrade.core.model.Confidence
import com.cardpregrade.core.model.Grade
import com.cardpregrade.core.model.SubgradeCategory
import com.cardpregrade.core.model.SubgradeEstimate

/**
 * Decides whether to show "Potential BGS 10 candidate" / "Potential Black Label candidate —
 * experimental". These are *flags for the user to consider*, never grade claims.
 *
 * A candidate requires ALL four categories to be estimated with a lower bound of 10 AND each
 * category's confidence to reach [minCategoryConfidence] AND every photo to pass the quality
 * gate. An overall score alone can never produce a candidate.
 */
class CandidateAssessor(private val minCategoryConfidence: Confidence) {

    fun bgs10(subgrades: List<SubgradeEstimate>, allImagesAcceptable: Boolean): CandidateIndicator =
        assess(subgrades, allImagesAcceptable, experimental = false)

    /** Same bar as [bgs10], always marked experimental; stricter criteria will be added from real data. */
    fun blackLabel(subgrades: List<SubgradeEstimate>, allImagesAcceptable: Boolean): CandidateIndicator =
        assess(subgrades, allImagesAcceptable, experimental = true)

    private fun assess(
        subgrades: List<SubgradeEstimate>,
        allImagesAcceptable: Boolean,
        experimental: Boolean,
    ): CandidateIndicator {
        val reasons = mutableListOf<String>()
        if (!allImagesAcceptable) reasons += "Not all photographs passed the quality check."

        val byCategory = subgrades.associateBy { it.category }
        for (category in SubgradeCategory.entries) {
            val estimate = byCategory[category]
            val range = estimate?.range
            when {
                estimate == null || range == null -> reasons += "${category.label} was not estimated."
                range.low < TEN -> reasons += "${category.label} estimate is below 10."
                estimate.confidence < minCategoryConfidence -> reasons += "${category.label} confidence is too low."
            }
        }
        val status = if (reasons.isEmpty()) CandidateStatus.POTENTIAL_CANDIDATE else CandidateStatus.NOT_INDICATED
        return CandidateIndicator(status, experimental, reasons)
    }

    private val SubgradeCategory.label: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }

    private companion object {
        val TEN = Grade.of(10)
    }
}
