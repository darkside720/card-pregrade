package com.cardpregrade.core.cv.geometry

import kotlin.math.abs
import kotlin.math.hypot

/** A point in source-image pixel coordinates (origin top-left, y down). */
data class PixelPoint(val x: Double, val y: Double) {
    fun distanceTo(other: PixelPoint): Double = hypot(x - other.x, y - other.y)
}

/**
 * A card outline in a source photograph with corners in a fixed, known order.
 * Detectors return arbitrary point order; [fromUnordered] canonicalizes it.
 */
data class Quadrilateral(
    val topLeft: PixelPoint,
    val topRight: PixelPoint,
    val bottomRight: PixelPoint,
    val bottomLeft: PixelPoint,
) {
    val points: List<PixelPoint> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** Polygon area via the shoelace formula. */
    val area: Double
        get() {
            val p = points
            var sum = 0.0
            for (i in p.indices) {
                val a = p[i]
                val b = p[(i + 1) % p.size]
                sum += a.x * b.y - b.x * a.y
            }
            return abs(sum) / 2.0
        }

    /** True if all turns have the same sign, i.e. the outline is convex and non-self-intersecting. */
    val isConvex: Boolean
        get() {
            val p = points
            val signs = p.indices.map { i ->
                val a = p[i]
                val b = p[(i + 1) % 4]
                val c = p[(i + 2) % 4]
                val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
                cross > 0
            }
            return signs.all { it } || signs.none { it }
        }

    /** Mean width / mean height of the outline (≈ physical aspect ratio when viewed straight on). */
    val aspectRatio: Double
        get() {
            val width = (topLeft.distanceTo(topRight) + bottomLeft.distanceTo(bottomRight)) / 2
            val height = (topLeft.distanceTo(bottomLeft) + topRight.distanceTo(bottomRight)) / 2
            return width / height
        }

    companion object {
        /**
         * Orders four points as TL, TR, BR, BL for an approximately upright card:
         * TL has the smallest x+y, BR the largest; TR has the smallest y−x, BL the largest.
         * Heavily rotated (~45°) cards are ambiguous and must be handled by the detector.
         */
        fun fromUnordered(points: List<PixelPoint>): Quadrilateral {
            require(points.size == 4) { "Exactly four points required, got ${points.size}" }
            val bySum = points.sortedBy { it.x + it.y }
            val byDiff = points.sortedBy { it.y - it.x }
            val quad = Quadrilateral(
                topLeft = bySum.first(),
                topRight = byDiff.first(),
                bottomRight = bySum.last(),
                bottomLeft = byDiff.last(),
            )
            require(quad.points.toSet().size == 4) { "Points could not be ordered unambiguously" }
            return quad
        }
    }
}
