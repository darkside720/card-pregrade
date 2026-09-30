package com.cardpregrade.core.model

enum class DefectCategory {
    WHITENING,
    EDGE_CHIP,
    CORNER_DAMAGE,
    IRREGULAR_CUT,
    SCRATCH,
    PRINT_LINE,
    DENT,
    STAIN,
    CENTERING,
    UNKNOWN,
}

enum class DefectSeverity { TRACE, MINOR, MODERATE, MAJOR }

/**
 * Where a defect is on a card side. Coordinates are normalized to the perspective-corrected
 * image of [CardDefect.side] so the UI can draw and zoom to the exact spot.
 */
data class DefectLocation(
    val region: CardRegion,
    val boundingBox: NormalizedRect,
    /** Optional precise outline; [boundingBox] must enclose it. */
    val polygon: List<NormalizedPoint>? = null,
) {
    init {
        polygon?.let { points ->
            require(points.size >= 3) { "A polygon needs at least 3 points" }
            require(points.all { it.x in boundingBox.left..boundingBox.right && it.y in boundingBox.top..boundingBox.bottom }) {
                "Polygon must lie within the bounding box"
            }
        }
    }

    /** Human-readable position, e.g. "right edge, upper section". */
    fun describe(): String {
        val base = when (region) {
            CardRegion.CORNER_TOP_LEFT -> "top-left corner"
            CardRegion.CORNER_TOP_RIGHT -> "top-right corner"
            CardRegion.CORNER_BOTTOM_RIGHT -> "bottom-right corner"
            CardRegion.CORNER_BOTTOM_LEFT -> "bottom-left corner"
            CardRegion.EDGE_TOP -> "top edge"
            CardRegion.EDGE_RIGHT -> "right edge"
            CardRegion.EDGE_BOTTOM -> "bottom edge"
            CardRegion.EDGE_LEFT -> "left edge"
            CardRegion.BORDER -> "border"
            CardRegion.SURFACE -> "surface"
            CardRegion.WHOLE_CARD -> "whole card"
        }
        val section = when (region) {
            CardRegion.EDGE_LEFT, CardRegion.EDGE_RIGHT -> thirds(boundingBox.centerY, "upper", "middle", "lower")
            CardRegion.EDGE_TOP, CardRegion.EDGE_BOTTOM -> thirds(boundingBox.centerX, "left", "center", "right")
            else -> null
        }
        return if (section == null) base else "$base, $section section"
    }

    private fun thirds(v: Double, a: String, b: String, c: String) = when {
        v < 1.0 / 3 -> a
        v < 2.0 / 3 -> b
        else -> c
    }
}

/** Normalized representation of a single potential defect. */
data class CardDefect(
    val id: String,
    val side: CardSide,
    val category: DefectCategory,
    val location: DefectLocation,
    val severity: DefectSeverity,
    val confidence: Confidence,
    /** User-facing wording; must use hedged language ("Possible whitening"). */
    val description: String,
    val sourceImageId: String,
    val algorithmVersion: AlgorithmVersion,
    val maturity: DetectionMaturity,
)
