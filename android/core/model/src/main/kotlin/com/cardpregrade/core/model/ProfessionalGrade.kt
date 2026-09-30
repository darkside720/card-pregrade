package com.cardpregrade.core.model

import java.time.Instant
import java.time.LocalDate

/** BGS subgrades as printed on the label, when available. */
data class BgsSubgrades(
    val centering: Grade?,
    val corners: Grade?,
    val edges: Grade?,
    val surface: Grade?,
) {
    fun get(category: SubgradeCategory): Grade? = when (category) {
        SubgradeCategory.CENTERING -> centering
        SubgradeCategory.CORNERS -> corners
        SubgradeCategory.EDGES -> edges
        SubgradeCategory.SURFACE -> surface
    }
}

/**
 * The actual result from a professional grading company, entered by the user after the
 * card returns. This is the ground truth for the feedback loop.
 */
data class ProfessionalGrade(
    val id: String,
    val cardId: String,
    val company: GradingCompany,
    val overall: Grade,
    /** Label qualifiers such as "Pristine", "Black Label", "OC". Free text. */
    val qualifier: String? = null,
    val bgsSubgrades: BgsSubgrades? = null,
    val certificationNumber: String? = null,
    val submittedOn: LocalDate? = null,
    val gradedOn: LocalDate? = null,
    val recordedAt: Instant,
    val notes: String? = null,
)
