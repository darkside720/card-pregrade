package com.cardpregrade.core.cv.geometry

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * An infinite line `a·x + b·y + c = 0` in continuous [PixelPoint] coordinates (y down).
 *
 * Invariants, established by the factories (there is no public constructor):
 * - **Unit normal:** (a, b) is normalised, a² + b² = 1 up to floating-point rounding, so
 *   [signedDistance] is a true Euclidean distance and vertical lines need no special case.
 * - **Canonical sign:** a > 0, or a == 0 and b > 0 (exact comparisons, no tolerance). The two
 *   coefficient triples describing one geometric line differ only by sign, so this picks exactly
 *   one; −0.0 is stored as 0.0. Lines built from the same points in either order are equal.
 * - Every coefficient is finite and |c| ≤ [MAX_ABS_OFFSET], so every derived quantity
 *   ([intersection] coordinates, parallel separations) is finite as well.
 *
 * The normal therefore points right for a vertical line and down for a horizontal one, and
 * [signedDistance] is positive on that side.
 */
class Line private constructor(val a: Double, val b: Double, val c: Double) {

    /** Positive on the side the normal (a, b) points to, zero on the line, in pixels. */
    fun signedDistance(point: PixelPoint): Double = a * point.x + b * point.y + c

    fun distance(point: PixelPoint): Double = abs(signedDistance(point))

    /**
     * Where this line meets [other].
     *
     * With unit normals the determinant `a₁b₂ − a₂b₁` equals sin θ for the angle θ between the
     * lines. If |sin θ| ≤ [PARALLEL_SIN_TOLERANCE] the lines are treated as parallel: the
     * solution would divide by a determinant within a few million ulps of zero, so its rounding
     * error could be as large as the answer. The result is then [LineIntersection.Coincident]
     * when the offsets agree to within [COINCIDENT_RELATIVE_TOLERANCE] · max(1, |c₁|, |c₂|), and
     * [LineIntersection.Parallel] otherwise. The band is relative because an angle of up to 1e-9
     * between the normals already moves the offset by up to 1e-9 · |c| (|c| is the line's
     * distance from the origin), and because offsets built from far-away points carry rounding
     * relative to those points: at image-scale offsets (|c| ≤ 1e4 px) it is at most 1e-5 px, while
     * at |c| = 1e12 px parallel lines up to 1000 px apart count as coincident.
     *
     * Both tolerances are numerical-safety limits, not geometric or detector policy: a
     * well-conditioned intersection is returned however far away it lies (at most about 2e159 px,
     * given [MAX_ABS_OFFSET]), and callers decide whether it is useful. The result does not depend
     * on argument order.
     */
    fun intersection(other: Line): LineIntersection {
        val det = a * other.b - other.a * b
        if (abs(det) <= PARALLEL_SIN_TOLERANCE) {
            // Nearly parallel canonical normals can still point opposite ways (e.g. (ε, 1) and (ε', −1)).
            val sameDirection = a * other.a + b * other.b >= 0.0
            val separation = abs(if (sameDirection) c - other.c else c + other.c)
            val scale = max(1.0, max(abs(c), abs(other.c)))
            return if (separation <= COINCIDENT_RELATIVE_TOLERANCE * scale) {
                LineIntersection.Coincident
            } else {
                LineIntersection.Parallel(separation)
            }
        }
        // Swapping the arguments negates numerators and det together; `+ 0.0` keeps an exact zero
        // from becoming −0.0 in one order only.
        val x = (b * other.c - other.b * c) / det + 0.0
        val y = (other.a * c - a * other.c) / det + 0.0
        return LineIntersection.Point(PixelPoint(x, y))
    }

    override fun equals(other: Any?): Boolean = other is Line && a == other.a && b == other.b && c == other.c

    override fun hashCode(): Int = (a.hashCode() * 31 + b.hashCode()) * 31 + c.hashCode()

    override fun toString(): String = "Line(a=$a, b=$b, c=$c)"

    companion object {
        /** |sin θ| at or below which two lines are treated as parallel; see [intersection]. */
        const val PARALLEL_SIN_TOLERANCE = 1e-9

        /** Relative offset agreement at which parallel lines are treated as the same line. */
        const val COINCIDENT_RELATIVE_TOLERANCE = 1e-9

        /**
         * Largest |c| (distance from the origin, in pixels) a line may have, and the largest
         * |coordinate| [through] accepts. Far beyond any image, it keeps intersection numerators
         * (≤ 2·1e150) divided by a determinant above [PARALLEL_SIN_TOLERANCE] finite.
         */
        const val MAX_ABS_OFFSET = 1e150

        /**
         * The line `a·x + b·y + c = 0`, normalised and canonically signed; null if a coefficient is
         * not finite, a = b = 0, or the normalised |c| exceeds [MAX_ABS_OFFSET].
         */
        fun of(a: Double, b: Double, c: Double): Line? {
            if (!a.isFinite() || !b.isFinite() || !c.isFinite()) return null
            // Divide by the larger of |a|, |b| first and by the hypotenuse (in [1, √2]) second, so
            // neither the norm nor the quotients can overflow or lose precision to subnormals.
            val m = max(abs(a), abs(b))
            if (m == 0.0) return null
            val am = a / m
            val bm = b / m
            val h = hypot(am, bm)
            var na = am / h
            var nb = bm / h
            var nc = c / m / h
            if (!nc.isFinite() || abs(nc) > MAX_ABS_OFFSET) return null
            // Sign is decided on the normalised values, so an underflowed a cannot leave b < 0.
            if (na < 0.0 || (na == 0.0 && nb < 0.0)) {
                na = -na
                nb = -nb
                nc = -nc
            }
            // `+ 0.0` turns −0.0 into 0.0 so equal lines compare equal.
            return Line(na + 0.0, nb + 0.0, nc + 0.0)
        }

        /**
         * The line through [p] and [q]; null if they coincide, are not finite, or have a coordinate
         * beyond ±[MAX_ABS_OFFSET] (which keeps every intermediate finite). The offset is taken at
         * their midpoint, which is symmetric in p and q, so `through(p, q) == through(q, p)`.
         */
        fun through(p: PixelPoint, q: PixelPoint): Line? {
            if (!inRange(p.x) || !inRange(p.y) || !inRange(q.x) || !inRange(q.y)) return null
            if (p == q) return null
            val nx = p.y - q.y
            val ny = q.x - p.x
            val mx = (p.x + q.x) / 2
            val my = (p.y + q.y) / 2
            return of(nx, ny, -(nx * mx + ny * my))
        }

        /** Finite and within ±[MAX_ABS_OFFSET]; NaN fails the comparison. */
        private fun inRange(v: Double): Boolean = abs(v) <= MAX_ABS_OFFSET
    }
}

sealed interface LineIntersection {
    data class Point(val point: PixelPoint) : LineIntersection

    /** Parallel within [Line.PARALLEL_SIN_TOLERANCE] and [separation] pixels apart. */
    data class Parallel(val separation: Double) : LineIntersection

    /** Parallel and at the same offset: the same line up to rounding. */
    data object Coincident : LineIntersection
}
