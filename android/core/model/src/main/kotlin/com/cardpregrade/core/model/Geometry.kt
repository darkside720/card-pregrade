package com.cardpregrade.core.model

/**
 * A point in normalized coordinates of a perspective-corrected card image:
 * (0,0) is the card's top-left corner, (1,1) its bottom-right corner.
 * Using normalized coordinates keeps defect locations independent of image resolution.
 */
data class NormalizedPoint(val x: Double, val y: Double) {
    init {
        require(x in 0.0..1.0 && y in 0.0..1.0) { "Normalized point out of range: ($x, $y)" }
    }
}

/** Axis-aligned rectangle in normalized card coordinates. */
data class NormalizedRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    init {
        require(left in 0.0..1.0 && right in 0.0..1.0 && top in 0.0..1.0 && bottom in 0.0..1.0) {
            "Normalized rect out of range: $this"
        }
        require(left < right && top < bottom) { "Degenerate normalized rect: $this" }
    }

    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val centerX: Double get() = (left + right) / 2
    val centerY: Double get() = (top + bottom) / 2

    companion object {
        val FULL = NormalizedRect(0.0, 0.0, 1.0, 1.0)
    }
}

enum class CardCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT }

enum class CardEdge { TOP, RIGHT, BOTTOM, LEFT }

/** Coarse region of the card a defect or analysis result belongs to. */
enum class CardRegion {
    CORNER_TOP_LEFT,
    CORNER_TOP_RIGHT,
    CORNER_BOTTOM_RIGHT,
    CORNER_BOTTOM_LEFT,
    EDGE_TOP,
    EDGE_RIGHT,
    EDGE_BOTTOM,
    EDGE_LEFT,
    BORDER,
    SURFACE,
    WHOLE_CARD,
    ;

    companion object {
        fun of(corner: CardCorner): CardRegion = when (corner) {
            CardCorner.TOP_LEFT -> CORNER_TOP_LEFT
            CardCorner.TOP_RIGHT -> CORNER_TOP_RIGHT
            CardCorner.BOTTOM_RIGHT -> CORNER_BOTTOM_RIGHT
            CardCorner.BOTTOM_LEFT -> CORNER_BOTTOM_LEFT
        }

        fun of(edge: CardEdge): CardRegion = when (edge) {
            CardEdge.TOP -> EDGE_TOP
            CardEdge.RIGHT -> EDGE_RIGHT
            CardEdge.BOTTOM -> EDGE_BOTTOM
            CardEdge.LEFT -> EDGE_LEFT
        }
    }
}
