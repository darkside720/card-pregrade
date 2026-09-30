package com.cardpregrade.core.model

import java.time.Instant

enum class InspectionWarningCode {
    DEMO_DATA,
    LOW_IMAGE_QUALITY,
    MISSING_CAPTURE,
    CENTERING_UNAVAILABLE,
    EXPERIMENTAL_DETECTIONS_PRESENT,
    SURFACE_ANALYSIS_LIMITED,
    CARD_NOT_IDENTIFIED,
    PHOTOGRAPHIC_LIMITATIONS,
}

data class InspectionWarning(val code: InspectionWarningCode, val message: String)

/** Complete output of one inspection of one scan session. */
data class InspectionResult(
    val id: String,
    val sessionId: String,
    val cardId: String,
    val cardGame: CardGame,
    /** Display label snapshot, e.g. "Lugia 149/147" or "Unknown Card". */
    val cardLabel: String,
    val createdAt: Instant,
    val imageQuality: List<ImageQualityResult>,
    val centering: List<CenteringResult>,
    val corners: List<CornerResult>,
    val edges: List<EdgeResult>,
    val surface: List<SurfaceResult>,
    /** All potential defects across regions (the region results reference the same objects). */
    val defects: List<CardDefect>,
    val estimate: GradeEstimate,
    val overallConfidence: Confidence,
    val warnings: List<InspectionWarning>,
    val provenance: AnalysisProvenance,
) {
    fun subgrade(category: SubgradeCategory): SubgradeEstimate? =
        estimate.subgrades.firstOrNull { it.category == category }
}
