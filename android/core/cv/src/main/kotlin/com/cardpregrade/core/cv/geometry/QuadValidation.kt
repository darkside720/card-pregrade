package com.cardpregrade.core.cv.geometry

import kotlin.math.abs
import kotlin.math.max

/** Why a quadrilateral failed validation. Ordered roughly from structural to policy checks. */
enum class QuadRejection {
    /** A corner has a NaN or infinite coordinate. No other check is performed. */
    NON_FINITE,

    /** Coincident corners or three consecutive collinear corners (a triangle or a line). */
    DEGENERATE,

    /** Opposite sides cross (e.g. two corners swapped). */
    SELF_INTERSECTING,

    /** Simple outline wound against the TL → TR → BR → BL convention (mirrored corner labels). */
    WRONG_WINDING,

    /** Not convex (concave, or self-intersecting). */
    NOT_CONVEX,

    /** Area below [QuadValidationPolicy.minAreaFraction] of the image. */
    AREA_TOO_SMALL,

    /** A side shorter than [QuadValidationPolicy.minSideLengthPx]. */
    SIDE_TOO_SHORT,

    /** A corner outside the image beyond [QuadValidationPolicy.boundsTolerancePx]. */
    OUT_OF_BOUNDS,

    /** An interior angle outside [QuadValidationPolicy.interiorAngleRangeDeg]. */
    ANGLE_OUT_OF_RANGE,

    /** Aspect ratio outside [QuadValidationPolicy.aspect]. */
    ASPECT_OUT_OF_RANGE,
}

/** Expected width/height ratio ([Quadrilateral.aspectRatio]) with an allowed relative deviation. */
data class AspectExpectation(val ratio: Double, val relativeTolerance: Double) {
    init {
        require(ratio > 0 && ratio.isFinite()) { "Aspect ratio must be positive: $ratio" }
        require(relativeTolerance >= 0 && relativeTolerance.isFinite()) { "Tolerance must be non-negative: $relativeTolerance" }
    }
}

/**
 * Thresholds for [QuadValidation]. Every field is **required and has no default**: thresholds
 * belong to the caller (e.g. a detector, tuned against synthetic fixtures), not to this
 * geometry utility. Pass `null` to skip a check explicitly.
 */
data class QuadValidationPolicy(
    /** Minimum area as a fraction of the image area, in (0, 1]. */
    val minAreaFraction: Double?,
    /** Minimum side length in pixels of the image the quad is expressed in. */
    val minSideLengthPx: Double?,
    /**
     * How far a corner may lie outside the image [0, width] × [0, height]. Negative values
     * require corners to be that far *inside*.
     */
    val boundsTolerancePx: Double?,
    /** Allowed interior angles in degrees. */
    val interiorAngleRangeDeg: ClosedFloatingPointRange<Double>?,
    /** Expected aspect ratio and tolerance. */
    val aspect: AspectExpectation?,
) {
    init {
        minAreaFraction?.let { require(it > 0 && it <= 1) { "minAreaFraction must be in (0, 1]: $it" } }
        minSideLengthPx?.let { require(it >= 0 && it.isFinite()) { "minSideLengthPx must be non-negative: $it" } }
        boundsTolerancePx?.let { require(it.isFinite()) { "boundsTolerancePx must be finite" } }
        interiorAngleRangeDeg?.let {
            require(it.start >= 0 && it.endInclusive <= 360 && it.start <= it.endInclusive) { "Invalid angle range: $it" }
        }
    }

    companion object {
        /** Structural checks only (non-finite, degenerate, self-intersection, winding, convexity). */
        val STRUCTURAL_ONLY = QuadValidationPolicy(null, null, null, null, null)
    }
}

/**
 * Validation outcome: every applicable [rejections] plus the measurements behind them (as
 * evidence for diagnostics). Measurements may be NaN for non-finite or degenerate outlines.
 */
data class QuadValidationResult(
    val rejections: Set<QuadRejection>,
    val areaFraction: Double,
    val minSideLengthPx: Double,
    val interiorAnglesDeg: List<Double>,
    val aspectRatio: Double,
) {
    val isValid: Boolean get() = rejections.isEmpty()
}

/**
 * Checks a [Quadrilateral] (TL/TR/BR/BL) in an image of [PixelSize]. Never throws for bad
 * geometry; it reports every failed check.
 *
 * Structural checks always run. Angle, aspect, convexity and winding are skipped for
 * DEGENERATE outlines (they are undefined there); area, side length and bounds still apply.
 */
object QuadValidation {

    /** Relative tolerance for "zero" side lengths / areas / turns; numerical, not a policy. */
    private const val DEGENERATE_EPSILON = 1e-9

    fun validate(quad: Quadrilateral, imageSize: PixelSize, policy: QuadValidationPolicy): QuadValidationResult {
        val points = quad.points
        if (points.any { !it.x.isFinite() || !it.y.isFinite() }) {
            return QuadValidationResult(setOf(QuadRejection.NON_FINITE), Double.NaN, Double.NaN, List(4) { Double.NaN }, Double.NaN)
        }

        val rejections = sortedSetOf<QuadRejection>()
        val sides = quad.sideLengths
        val minSide = sides.min()
        val areaFraction = quad.area / (imageSize.width.toDouble() * imageSize.height)
        val angles = quad.interiorAnglesDeg
        val aspect = quad.aspectRatio

        val degenerate = isDegenerate(quad)
        if (degenerate) {
            rejections += QuadRejection.DEGENERATE
        } else {
            val selfIntersecting = quad.isSelfIntersecting
            if (selfIntersecting) rejections += QuadRejection.SELF_INTERSECTING
            if (!selfIntersecting && quad.signedArea < 0) rejections += QuadRejection.WRONG_WINDING
            if (!quad.isConvex) rejections += QuadRejection.NOT_CONVEX
        }

        policy.minAreaFraction?.let { if (areaFraction < it) rejections += QuadRejection.AREA_TOO_SMALL }
        policy.minSideLengthPx?.let { if (minSide < it) rejections += QuadRejection.SIDE_TOO_SHORT }
        policy.boundsTolerancePx?.let { tol ->
            val outside = points.any { p ->
                p.x < -tol || p.y < -tol || p.x > imageSize.width + tol || p.y > imageSize.height + tol
            }
            if (outside) rejections += QuadRejection.OUT_OF_BOUNDS
        }
        if (!degenerate) {
            policy.interiorAngleRangeDeg?.let { range ->
                if (angles.any { it.isNaN() || it !in range }) rejections += QuadRejection.ANGLE_OUT_OF_RANGE
            }
            policy.aspect?.let { expected ->
                val deviation = abs(aspect / expected.ratio - 1.0)
                if (!deviation.isFinite() || deviation > expected.relativeTolerance) rejections += QuadRejection.ASPECT_OUT_OF_RANGE
            }
        }

        return QuadValidationResult(rejections, areaFraction, minSide, angles, aspect)
    }

    private fun isDegenerate(quad: Quadrilateral): Boolean {
        val p = quad.points
        var extent = 0.0
        for (i in p.indices) for (j in i + 1 until p.size) extent = max(extent, p[i].distanceTo(p[j]))
        if (extent == 0.0) return true
        if (quad.sideLengths.any { it <= DEGENERATE_EPSILON * extent }) return true
        // Three consecutive corners on one line make the outline a triangle. (A simple quad with
        // zero area always has such a triple; the shoelace area is not used because it can be
        // zero for a symmetric bow-tie, which must be reported as SELF_INTERSECTING instead.)
        return p.indices.any { i ->
            val a = p[(i + 3) % 4]
            val b = p[i]
            val c = p[(i + 1) % 4]
            abs((b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)) <= DEGENERATE_EPSILON * extent * extent
        }
    }
}
