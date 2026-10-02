package com.cardpregrade.core.cv.geometry

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.sign

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
        get() = abs(signedArea)

    /**
     * Shoelace area with sign. In image coordinates (y down) the TL → TR → BR → BL convention
     * runs clockwise on screen and gives a **positive** value; a negative value means the
     * corners are wound the other way. Meaningless for self-intersecting outlines.
     */
    val signedArea: Double
        get() {
            val p = points
            var sum = 0.0
            for (i in p.indices) {
                val a = p[i]
                val b = p[(i + 1) % p.size]
                sum += a.x * b.y - b.x * a.y
            }
            return sum / 2.0
        }

    /** Side lengths in order top (TL–TR), right (TR–BR), bottom (BR–BL), left (BL–TL). */
    val sideLengths: List<Double>
        get() = points.indices.map { i -> points[i].distanceTo(points[(i + 1) % 4]) }

    /**
     * Interior angles in degrees at TL, TR, BR, BL. A reflex vertex of a concave outline is
     * reported as > 180°, so a simple (non-self-intersecting) quadrilateral sums to 360°.
     * A vertex with a zero-length adjacent side yields NaN. Not meaningful for self-intersecting
     * outlines.
     */
    val interiorAnglesDeg: List<Double>
        get() {
            val p = points
            val orientation = sign(signedArea)
            return p.indices.map { i ->
                val prev = p[(i + 3) % 4]
                val at = p[i]
                val next = p[(i + 1) % 4]
                val ax = prev.x - at.x
                val ay = prev.y - at.y
                val bx = next.x - at.x
                val by = next.y - at.y
                val lengths = hypot(ax, ay) * hypot(bx, by)
                if (lengths == 0.0) return@map Double.NaN
                val unsigned = Math.toDegrees(acos(((ax * bx + ay * by) / lengths).coerceIn(-1.0, 1.0)))
                // Turn direction at this vertex; opposite to the outline's winding means reflex.
                val turn = cross(at.x - prev.x, at.y - prev.y, next.x - at.x, next.y - at.y)
                if (orientation != 0.0 && sign(turn) == -orientation) 360.0 - unsigned else unsigned
            }
        }

    /** True if opposite sides cross each other (a "bow-tie", e.g. two corners swapped). */
    val isSelfIntersecting: Boolean
        get() = segmentsCross(topLeft, topRight, bottomRight, bottomLeft) ||
            segmentsCross(topRight, bottomRight, bottomLeft, topLeft)

    /**
     * True if every turn is strictly positive or every turn is strictly negative, i.e. the outline
     * is convex and non-self-intersecting (either winding). A zero turn (collinear corners) is
     * not convex.
     */
    val isConvex: Boolean
        get() {
            val p = points
            val turns = p.indices.map { i ->
                val a = p[i]
                val b = p[(i + 1) % 4]
                val c = p[(i + 2) % 4]
                cross(b.x - a.x, b.y - a.y, c.x - b.x, c.y - b.y)
            }
            return turns.all { it > 0 } || turns.all { it < 0 }
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

        private fun cross(ax: Double, ay: Double, bx: Double, by: Double): Double = ax * by - ay * bx

        /** Proper crossing of segments p1–p2 and q1–q2 (touching or collinear overlap is not counted). */
        private fun segmentsCross(p1: PixelPoint, p2: PixelPoint, q1: PixelPoint, q2: PixelPoint): Boolean {
            val d1 = cross(p2.x - p1.x, p2.y - p1.y, q1.x - p1.x, q1.y - p1.y)
            val d2 = cross(p2.x - p1.x, p2.y - p1.y, q2.x - p1.x, q2.y - p1.y)
            val d3 = cross(q2.x - q1.x, q2.y - q1.y, p1.x - q1.x, p1.y - q1.y)
            val d4 = cross(q2.x - q1.x, q2.y - q1.y, p2.x - q1.x, p2.y - q1.y)
            return sign(d1) * sign(d2) < 0 && sign(d3) * sign(d4) < 0
        }
    }
}
