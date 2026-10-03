package com.cardpregrade.core.cv.geometry

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

enum class LineFitRejection {
    /** Fewer than two points. */
    TOO_FEW_POINTS,

    /** A coordinate is NaN or infinite. */
    NON_FINITE_POINT,

    /** Every point is exactly the same point: no direction exists. */
    IDENTICAL_POINTS,

    /**
     * The scatter is isotropic to within rounding (e.g. the corners of a square), so every
     * direction fits equally well; see [LineFitter.ISOTROPY_TOLERANCE].
     */
    NO_DOMINANT_DIRECTION,

    /** The centroid overflowed Double, or the fitted line lies beyond [Line.MAX_ABS_OFFSET]. */
    NUMERIC_OVERFLOW,
}

sealed interface LineFitResult {
    /**
     * Measurements of the fit; no judgement of whether it is good enough.
     *
     * @property pointCount number of points fitted (repeated points count each time).
     * @property rmsResidualPx root-mean-square orthogonal distance from the points to [line].
     * @property maxAbsResidualPx largest orthogonal distance from a point to [line].
     */
    data class Fitted(
        val line: Line,
        val pointCount: Int,
        val rmsResidualPx: Double,
        val maxAbsResidualPx: Double,
    ) : LineFitResult

    data class Rejected(val reason: LineFitRejection, val detail: String) : LineFitResult
}

/**
 * Total-least-squares (orthogonal regression) line through caller-supplied points: the [Line]
 * minimising the sum of squared perpendicular distances. Symmetric in x and y, so vertical and
 * steep lines need no special case. It fits every point it is given with equal weight; choosing
 * the points (and rejecting outliers) is the caller's job.
 *
 * Formulation:
 * 1. centroid (x̄, ȳ) = mean of the points, except that a coordinate shared exactly by every
 *    point is used as is (a mean of identical values need not round back to the value);
 * 2. centred scatter Sxx = Σ(x − x̄)², Sxy = Σ(x − x̄)(y − ȳ), Syy = Σ(y − ȳ)², so large image
 *    coordinates do not swamp the spread. The centred offsets are first divided by their largest
 *    magnitude, which leaves the direction unchanged but keeps the sums from underflowing (points
 *    1e-200 apart) or overflowing;
 * 3. the major axis of [[Sxx, Sxy], [Sxy, Syy]] is at half the angle of (p, q) = (Sxx − Syy, 2·Sxy).
 *    With g = |(p, q)| (the eigenvalue gap) it points along (g + p, q) when p ≥ 0 and along
 *    (q, g − p) when p < 0: half-angle identities with no trigonometry and no cancellation, so
 *    exactly vertical or horizontal points give an exactly vertical or horizontal line;
 * 4. the fitted line passes through the centroid with the minor axis (−u_y, u_x) as normal and is
 *    normalised and canonicalised by [Line.of];
 * 5. residuals are the orthogonal distances a·(x − x̄) + b·(y − ȳ), evaluated in centred
 *    coordinates to avoid cancellation against a large offset c.
 *
 * Rejections: fewer than two points; any non-finite coordinate; all points identical (compared
 * exactly); scatter whose two eigenvalues differ by at most [ISOTROPY_TOLERANCE] of their sum;
 * a centroid that overflows or a line beyond [Line.MAX_ABS_OFFSET]. Two distinct points within
 * that range always fit (their scatter is perfectly anisotropic).
 *
 * Allocates only the result. The input is read by index in four passes (centroid, scale, scatter,
 * residuals), so callers should pass a random-access list.
 */
object LineFitter {

    /**
     * Eigenvalue gap (λ₁ − λ₂) / (λ₁ + λ₂) at or below which the scatter is treated as having no
     * direction. A numerical limit only: at 1e-12 the fitted direction is decided by rounding error,
     * not by the data. It is not a straightness or quality threshold.
     */
    const val ISOTROPY_TOLERANCE = 1e-12

    fun fit(points: List<PixelPoint>): LineFitResult {
        val n = points.size
        if (n < 2) return reject(LineFitRejection.TOO_FEW_POINTS, "$n point(s)")

        val first = points[0]
        var sameX = true
        var sameY = true
        var sumX = 0.0
        var sumY = 0.0
        for (i in 0 until n) {
            val p = points[i]
            if (!p.x.isFinite() || !p.y.isFinite()) return reject(LineFitRejection.NON_FINITE_POINT, "point $i = $p")
            if (p.x != first.x) sameX = false
            if (p.y != first.y) sameY = false
            sumX += p.x
            sumY += p.y
        }
        if (sameX && sameY) return reject(LineFitRejection.IDENTICAL_POINTS, "$n copies of $first")
        val cx = if (sameX) first.x else sumX / n
        val cy = if (sameY) first.y else sumY / n
        if (!cx.isFinite() || !cy.isFinite()) return reject(LineFitRejection.NUMERIC_OVERFLOW, "centroid ($cx, $cy)")

        // Largest centred offset; positive because the points are not all identical.
        var scale = 0.0
        for (i in 0 until n) {
            scale = maxOf(scale, abs(points[i].x - cx), abs(points[i].y - cy))
        }
        var sxx = 0.0
        var sxy = 0.0
        var syy = 0.0
        for (i in 0 until n) {
            val dx = (points[i].x - cx) / scale
            val dy = (points[i].y - cy) / scale
            sxx += dx * dx
            sxy += dx * dy
            syy += dy * dy
        }
        // The point at distance `scale` contributes 1, so 1 ≤ trace ≤ 2n: no underflow or overflow.
        val trace = sxx + syy
        val gap = hypot(sxx - syy, 2.0 * sxy)
        if (gap <= ISOTROPY_TOLERANCE * trace) {
            return reject(LineFitRejection.NO_DOMINANT_DIRECTION, "scatter ($sxx, $sxy, $syy)")
        }

        // Major-axis direction (ux, uy) at half the angle of (p, q) = (Sxx − Syy, 2·Sxy), gap = |(p, q)|.
        // Each branch adds two non-negative terms, so neither cancels.
        val p = sxx - syy
        val q = 2.0 * sxy
        val ux = if (p >= 0.0) gap + p else q
        val uy = if (p >= 0.0) q else gap - p
        val nx = -uy
        val ny = ux
        val line = Line.of(nx, ny, -(nx * cx + ny * cy))
            ?: return reject(LineFitRejection.NUMERIC_OVERFLOW, "line through centroid ($cx, $cy) is out of range")

        var sumSq = 0.0
        var maxAbs = 0.0
        for (i in 0 until n) {
            val r = abs(line.a * (points[i].x - cx) + line.b * (points[i].y - cy))
            sumSq += r * r
            if (r > maxAbs) maxAbs = r
        }
        return LineFitResult.Fitted(line, n, sqrt(sumSq / n), maxAbs)
    }

    private fun reject(reason: LineFitRejection, detail: String) = LineFitResult.Rejected(reason, detail)
}
