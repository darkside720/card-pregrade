package com.cardpregrade.core.cv.geometry

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * A planar projective transform (3×3 matrix acting on homogeneous pixel coordinates):
 *
 * ```
 * [x' w]   [h00 h01 h02] [x]
 * [y' w] = [h10 h11 h12] [y]
 * [   w]   [h20 h21 h22] [1]
 * ```
 *
 * Immutable. A homography is only defined up to scale; instances are normalized so that
 * h22 = 1 when h22 is not negligible, otherwise to unit Frobenius norm. Values are computed in
 * Double precision and should be compared with a tolerance, never for exact equality.
 *
 * Coordinates are continuous pixel coordinates (origin at the image's top-left corner, y down):
 * an image of width W spans x ∈ [0, W], and the centre of pixel column i is at x = i + 0.5.
 */
class Homography private constructor(private val m: DoubleArray) {

    /** Element at [row], [col] (each 0..2). */
    operator fun get(row: Int, col: Int): Double {
        require(row in 0..2 && col in 0..2) { "Index out of range: ($row, $col)" }
        return m[row * 3 + col]
    }

    /** Copy of the nine elements in row-major order. */
    fun toRowMajor(): List<Double> = m.toList()

    /** Determinant of the normalized matrix; useful as a diagnostic, not a quality score. */
    val determinant: Double get() = det3(m)

    /**
     * Projects [point]. Returns null when the point lies on (or numerically at) the line this
     * transform sends to infinity, or when the result is not finite.
     */
    fun apply(point: PixelPoint): PixelPoint? {
        val x = point.x
        val y = point.y
        val w = m[6] * x + m[7] * y + m[8]
        val wScale = abs(m[6] * x) + abs(m[7] * y) + abs(m[8])
        if (!w.isFinite() || abs(w) <= W_EPSILON * wScale) return null
        val px = (m[0] * x + m[1] * y + m[2]) / w
        val py = (m[3] * x + m[4] * y + m[5]) / w
        return if (px.isFinite() && py.isFinite()) PixelPoint(px, py) else null
    }

    /** The inverse transform, or [HomographyFailure.NOT_INVERTIBLE] for a (near-)singular matrix. */
    fun inverse(): HomographyResult {
        val det = det3(m)
        if (!isInvertible(m, det)) return HomographyResult.Failed(HomographyFailure.NOT_INVERTIBLE, "determinant $det")
        val a = m
        val adj = doubleArrayOf(
            a[4] * a[8] - a[5] * a[7], a[2] * a[7] - a[1] * a[8], a[1] * a[5] - a[2] * a[4],
            a[5] * a[6] - a[3] * a[8], a[0] * a[8] - a[2] * a[6], a[2] * a[3] - a[0] * a[5],
            a[3] * a[7] - a[4] * a[6], a[1] * a[6] - a[0] * a[7], a[0] * a[4] - a[1] * a[3],
        )
        return normalized(DoubleArray(9) { adj[it] / det })
    }

    override fun toString(): String = "Homography(${m.joinToString()})"

    companion object {
        val IDENTITY: Homography = Homography(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0))

        /**
         * Builds a homography from nine row-major elements. Fails with [HomographyFailure.NON_FINITE_INPUT]
         * for NaN/infinite values and [HomographyFailure.NOT_INVERTIBLE] for a (near-)singular matrix.
         */
        fun fromRowMajor(values: List<Double>): HomographyResult {
            require(values.size == 9) { "A homography has 9 elements, got ${values.size}" }
            if (values.any { !it.isFinite() }) return HomographyResult.Failed(HomographyFailure.NON_FINITE_INPUT, "matrix")
            val a = values.toDoubleArray()
            if (!isInvertible(a, det3(a))) return HomographyResult.Failed(HomographyFailure.NOT_INVERTIBLE, "determinant ${det3(a)}")
            return normalized(a)
        }

        /**
         * The unique homography mapping each corner of [source] to the corresponding corner of
         * [destination] (TL→TL, TR→TR, BR→BR, BL→BL).
         *
         * Solves the standard 8×8 linear system (h22 fixed to 1) by Gaussian elimination with
         * partial pivoting, after translating and scaling each point set to its centroid
         * (Hartley normalization) for numerical conditioning.
         *
         * Fails rather than guessing when either point set has a non-finite coordinate, repeated
         * points, or three (near-)collinear points, or when the system is singular. The h22 = 1
         * parametrization cannot represent a transform that sends the source centroid to infinity;
         * such inputs are reported as [HomographyFailure.SINGULAR_SYSTEM]. That cannot happen
         * between two convex quadrilaterals with the same winding.
         */
        fun fromFourPoints(source: Quadrilateral, destination: Quadrilateral): HomographyResult {
            checkPoints(source.points, "source")?.let { return it }
            checkPoints(destination.points, "destination")?.let { return it }

            val src = Normalization.of(source.points)
            val dst = Normalization.of(destination.points)
            val s = source.points.map(src::apply)
            val d = destination.points.map(dst::apply)

            // Augmented 8×9 system for h = (h00, h01, h02, h10, h11, h12, h20, h21), h22 = 1.
            val a = Array(8) { DoubleArray(9) }
            for (i in 0 until 4) {
                val (x, y) = s[i]
                val (u, v) = d[i]
                a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -x * u, -y * u, u)
                a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -x * v, -y * v, v)
            }
            val h = solve(a) ?: return HomographyResult.Failed(HomographyFailure.SINGULAR_SYSTEM, "pivot below tolerance")
            val normalizedH = doubleArrayOf(h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0)
            // Conditioning is judged in the dimensionless normalized frame: T_src and T_dst are
            // similarities, so the full matrix is invertible exactly when normalizedH is.
            if (!isInvertible(normalizedH, det3(normalizedH))) {
                return HomographyResult.Failed(HomographyFailure.SINGULAR_SYSTEM, "degenerate solution")
            }

            // Undo the normalization: H = T_dst⁻¹ · Hn · T_src.
            val full = multiply(dst.inverseMatrix(), multiply(normalizedH, src.matrix()))
            if (full.any { !it.isFinite() }) {
                return HomographyResult.Failed(HomographyFailure.SINGULAR_SYSTEM, "non-finite solution")
            }
            val result = normalized(full)
            if (result !is HomographyResult.Solved) return result

            // Self-check: the solution must reproduce the correspondences; otherwise the system was ill-conditioned.
            val tolerance = REPROJECTION_TOLERANCE * max(1.0, scaleOf(destination.points))
            val reproduces = source.points.zip(destination.points).all { (from, to) ->
                result.homography.apply(from)?.let { it.distanceTo(to) <= tolerance } ?: false
            }
            return if (reproduces) result else HomographyResult.Failed(HomographyFailure.SINGULAR_SYSTEM, "ill-conditioned")
        }

        /**
         * Convenience for rectification: maps [source] onto the axis-aligned rectangle
         * [0, width] × [0, height] (TL at the origin, continuous pixel coordinates).
         */
        fun fromQuadToRect(source: Quadrilateral, size: PixelSize): HomographyResult {
            val w = size.width.toDouble()
            val h = size.height.toDouble()
            val rect = Quadrilateral(PixelPoint(0.0, 0.0), PixelPoint(w, 0.0), PixelPoint(w, h), PixelPoint(0.0, h))
            return fromFourPoints(source, rect)
        }

        // --- numerics ---

        /** |w| at or below this fraction of its terms' magnitude is treated as "at infinity". */
        private const val W_EPSILON = 1e-12

        /** Points closer than this fraction of the point set's extent count as repeated. */
        private const val COINCIDENT_EPSILON = 1e-9

        /** Triangle area below this fraction of extent² counts as collinear. */
        private const val COLLINEAR_EPSILON = 1e-9

        /** Pivot magnitude below this (in normalized coordinates, entries ~1) means a singular system. */
        private const val PIVOT_EPSILON = 1e-10

        /** |det| below this fraction of the translation-balanced Frobenius norm cubed means not invertible. */
        private const val DETERMINANT_EPSILON = 1e-12

        /** Allowed correspondence error after solving, as a fraction of the destination extent. */
        private const val REPROJECTION_TOLERANCE = 1e-7

        private fun checkPoints(points: List<PixelPoint>, which: String): HomographyResult.Failed? {
            if (points.any { !it.x.isFinite() || !it.y.isFinite() }) {
                return HomographyResult.Failed(HomographyFailure.NON_FINITE_INPUT, which)
            }
            val scale = scaleOf(points)
            for (i in 0 until 4) for (j in i + 1 until 4) {
                if (points[i].distanceTo(points[j]) <= COINCIDENT_EPSILON * scale) {
                    return HomographyResult.Failed(HomographyFailure.REPEATED_POINTS, which)
                }
            }
            for (skip in 0 until 4) {
                val (a, b, c) = points.filterIndexed { index, _ -> index != skip }
                val twiceArea = abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x))
                if (twiceArea <= COLLINEAR_EPSILON * scale * scale) {
                    return HomographyResult.Failed(HomographyFailure.COLLINEAR_POINTS, which)
                }
            }
            return null
        }

        /** Largest pairwise distance (0 if all points coincide). */
        private fun scaleOf(points: List<PixelPoint>): Double {
            var best = 0.0
            for (i in points.indices) for (j in i + 1 until points.size) best = max(best, points[i].distanceTo(points[j]))
            return best
        }

        /** Gaussian elimination with partial pivoting on an n×(n+1) augmented matrix. Null if singular. */
        private fun solve(a: Array<DoubleArray>): DoubleArray? {
            val n = a.size
            for (col in 0 until n) {
                var pivotRow = col
                for (r in col + 1 until n) if (abs(a[r][col]) > abs(a[pivotRow][col])) pivotRow = r
                if (abs(a[pivotRow][col]) < PIVOT_EPSILON) return null
                if (pivotRow != col) a[col] = a[pivotRow].also { a[pivotRow] = a[col] }
                for (r in col + 1 until n) {
                    val factor = a[r][col] / a[col][col]
                    if (factor == 0.0) continue
                    for (c in col until n + 1) a[r][c] -= factor * a[col][c]
                }
            }
            val x = DoubleArray(n)
            for (r in n - 1 downTo 0) {
                var sum = a[r][n]
                for (c in r + 1 until n) sum -= a[r][c] * x[c]
                x[r] = sum / a[r][r]
            }
            return if (x.all { it.isFinite() }) x else null
        }

        private fun det3(a: DoubleArray): Double =
            a[0] * (a[4] * a[8] - a[5] * a[7]) - a[1] * (a[3] * a[8] - a[5] * a[6]) + a[2] * (a[3] * a[7] - a[4] * a[6])

        private fun frobenius(a: DoubleArray): Double = sqrt(a.sumOf { it * it })

        /**
         * Scale- and translation-aware singularity test. The elements mix units (linear part:
         * none, translation: px, perspective: 1/px), so a raw det/‖A‖³ test would call a harmless
         * large translation singular. The matrix is first re-expressed in a pixel unit L that
         * brings the translation to the linear part's magnitude, S⁻¹·A·S with S = diag(L, L, 1);
         * that similarity leaves the determinant and invertibility unchanged.
         */
        private fun isInvertible(a: DoubleArray, det: Double): Boolean {
            val linear = sqrt(a[0] * a[0] + a[1] * a[1] + a[3] * a[3] + a[4] * a[4])
            val translation = hypot(a[2], a[5])
            val l = if (linear > 0.0 && translation > linear) translation / linear else 1.0
            val balanced = doubleArrayOf(a[0], a[1], a[2] / l, a[3], a[4], a[5] / l, a[6] * l, a[7] * l, a[8])
            val norm = frobenius(balanced)
            return det.isFinite() && norm.isFinite() && norm > 0.0 && abs(det) > DETERMINANT_EPSILON * norm * norm * norm
        }

        private fun multiply(a: DoubleArray, b: DoubleArray): DoubleArray = DoubleArray(9) { i ->
            val r = i / 3
            val c = i % 3
            a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
        }

        /** Scales to h22 = 1 when h22 is not negligible relative to the matrix, else to unit norm. */
        private fun normalized(a: DoubleArray): HomographyResult {
            val norm = frobenius(a)
            if (!norm.isFinite() || norm == 0.0) return HomographyResult.Failed(HomographyFailure.NOT_INVERTIBLE, "zero matrix")
            val divisor = if (abs(a[8]) > W_EPSILON * norm) a[8] else norm
            val out = DoubleArray(9) { a[it] / divisor }
            return if (out.all { it.isFinite() }) {
                HomographyResult.Solved(Homography(out))
            } else {
                HomographyResult.Failed(HomographyFailure.NON_FINITE_INPUT, "normalization")
            }
        }
    }

    /** Similarity transform moving a point set's centroid to the origin with mean distance √2. */
    private class Normalization(private val cx: Double, private val cy: Double, private val scale: Double) {
        fun apply(p: PixelPoint): Pair<Double, Double> = (p.x - cx) * scale to (p.y - cy) * scale

        fun matrix(): DoubleArray = doubleArrayOf(scale, 0.0, -scale * cx, 0.0, scale, -scale * cy, 0.0, 0.0, 1.0)

        fun inverseMatrix(): DoubleArray = doubleArrayOf(1 / scale, 0.0, cx, 0.0, 1 / scale, cy, 0.0, 0.0, 1.0)

        companion object {
            fun of(points: List<PixelPoint>): Normalization {
                val cx = points.sumOf { it.x } / points.size
                val cy = points.sumOf { it.y } / points.size
                val meanDistance = points.sumOf { hypot(it.x - cx, it.y - cy) } / points.size
                return Normalization(cx, cy, sqrt(2.0) / meanDistance)
            }
        }
    }
}

/** Outcome of building or inverting a [Homography]; normal invalid geometry never throws. */
sealed interface HomographyResult {
    data class Solved(val homography: Homography) : HomographyResult

    /** [detail] names the offending input ("source" / "destination") or the numerical cause. */
    data class Failed(val reason: HomographyFailure, val detail: String? = null) : HomographyResult
}

enum class HomographyFailure {
    /** A coordinate or matrix element is NaN or infinite. */
    NON_FINITE_INPUT,

    /** Two points of one set coincide (within a tolerance relative to the set's extent). */
    REPEATED_POINTS,

    /** Three points of one set are (near-)collinear, so no unique homography exists. */
    COLLINEAR_POINTS,

    /** The linear system is singular or ill-conditioned; no reliable solution. */
    SINGULAR_SYSTEM,

    /** The matrix is (near-)singular and has no inverse. */
    NOT_INVERTIBLE,
}
