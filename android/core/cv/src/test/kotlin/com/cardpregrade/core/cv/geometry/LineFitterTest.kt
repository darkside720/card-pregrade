package com.cardpregrade.core.cv.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Expected lines are written down from the geometry the points were placed on, and expected
 * residuals from the hand-computed scatter in the comments.
 */
class LineFitterTest {

    private val r2 = sqrt(2.0)

    private fun pts(vararg xy: Pair<Double, Double>) = xy.map { PixelPoint(it.first, it.second) }

    private fun fitted(points: List<PixelPoint>): LineFitResult.Fitted =
        when (val r = LineFitter.fit(points)) {
            is LineFitResult.Fitted -> r
            is LineFitResult.Rejected -> fail("Rejected: $r") as Nothing
        }

    private fun assertRejected(reason: LineFitRejection, points: List<PixelPoint>) {
        val r = LineFitter.fit(points)
        if (r !is LineFitResult.Rejected) fail("Expected $reason, got $r") else assertEquals(reason, r.reason)
    }

    /** Compares with the canonical line [a]x + [b]y + [c] = 0 given already normalised. */
    private fun assertLine(a: Double, b: Double, c: Double, line: Line, tol: Double = 1e-12) {
        assertEquals("a of $line", a, line.a, tol)
        assertEquals("b of $line", b, line.b, tol)
        assertEquals("c of $line", c, line.c, tol * maxOf(1.0, abs(c)))
    }

    @Test
    fun `exact horizontal points`() {
        val f = fitted(pts(0.0 to 5.0, 3.0 to 5.0, 7.0 to 5.0, 10.0 to 5.0))
        assertLine(0.0, 1.0, -5.0, f.line)
        assertEquals("exactly horizontal", 0.0, f.line.a, 0.0)
        assertEquals(4, f.pointCount)
        assertEquals(0.0, f.rmsResidualPx, 1e-15)
        assertEquals(0.0, f.maxAbsResidualPx, 1e-15)
    }

    @Test
    fun `exact vertical points are a valid line not a degenerate one`() {
        val f = fitted(pts(4.0 to 0.0, 4.0 to 2.5, 4.0 to 9.0, 4.0 to -30.0))
        assertLine(1.0, 0.0, -4.0, f.line)
        assertEquals("exactly vertical", 0.0, f.line.b, 0.0)
        assertEquals(0.0, f.maxAbsResidualPx, 0.0)
    }

    @Test
    fun `exact diagonal points`() {
        // y = x → canonical (1/√2, −1/√2, 0).
        val f = fitted(pts(0.0 to 0.0, 1.0 to 1.0, 2.0 to 2.0, 5.0 to 5.0))
        assertLine(1 / r2, -1 / r2, 0.0, f.line)
        assertEquals(0.0, f.maxAbsResidualPx, 1e-14)
    }

    @Test
    fun `arbitrary oblique line`() {
        // 3x + 4y = 10 holds for (2, 1), (6, −2), (−2, 4), (10, −5) → (0.6, 0.8, −2).
        val f = fitted(pts(2.0 to 1.0, 6.0 to -2.0, -2.0 to 4.0, 10.0 to -5.0))
        assertLine(0.6, 0.8, -2.0, f.line)
        assertEquals(0.0, f.maxAbsResidualPx, 1e-14)
    }

    @Test
    fun `two points give the line through them`() {
        // (1, 2) → (4, 6): direction (3, 4)/5, normal (−4, 3)/5 → canonical (0.8, −0.6, c) with
        // 0.8·1 − 0.6·2 + c = 0 → c = 0.4.
        val f = fitted(pts(1.0 to 2.0, 4.0 to 6.0))
        assertLine(0.8, -0.6, 0.4, f.line)
        assertEquals(2, f.pointCount)
        assertEquals(0.0, f.maxAbsResidualPx, 1e-15)
    }

    @Test
    fun `input order does not change the fit`() {
        val base = pts(2.0 to 1.0, 6.0 to -2.0, -2.0 to 4.0, 10.0 to -5.0, 0.0 to 2.5)
        val orders = listOf(base, base.reversed(), listOf(base[3], base[0], base[4], base[2], base[1]))
        for (order in orders) assertLine(0.6, 0.8, -2.0, fitted(order).line)
    }

    @Test
    fun `large translated coordinates keep full precision`() {
        // The oblique points shifted by (1e6, 2e6): 0.6(x − 1e6) + 0.8(y − 2e6) − 2 = 0
        // → c = −2 − 6e5 − 1.6e6 = −2 200 002.
        val dx = 1e6
        val dy = 2e6
        val f = fitted(pts(2.0 + dx to 1.0 + dy, 6.0 + dx to -2.0 + dy, -2.0 + dx to 4.0 + dy, 10.0 + dx to -5.0 + dy))
        assertLine(0.6, 0.8, -2_200_002.0, f.line)
        assertEquals(0.0, f.maxAbsResidualPx, 1e-9)
        // (5, 5) was 5 px from the untranslated line; translated it still is.
        assertEquals(5.0, f.line.signedDistance(PixelPoint(5.0 + dx, 5.0 + dy)), 1e-8)
    }

    @Test
    fun `shallow line`() {
        // y = 0.001x + 3 ⇔ 0.001x − y + 3 = 0; canonical (a > 0) → (0.001, −1, 3) / √(1 + 1e-6).
        val h = sqrt(1 + 1e-6)
        val f = fitted((0..10).map { PixelPoint(100.0 * it, 0.1 * it + 3.0) })
        assertLine(0.001 / h, -1 / h, 3 / h, f.line)
        assertEquals(0.0, f.maxAbsResidualPx, 1e-12)
    }

    @Test
    fun `steep line`() {
        // x = 0.001y + 3 ⇔ x − 0.001y − 3 = 0 → (1, −0.001, −3) / √(1 + 1e-6).
        val h = sqrt(1 + 1e-6)
        val f = fitted((0..10).map { PixelPoint(0.1 * it + 3.0, 100.0 * it) })
        assertLine(1 / h, -0.001 / h, -3 / h, f.line)
        assertEquals(0.0, f.maxAbsResidualPx, 1e-12)
    }

    @Test
    fun `symmetric alternating noise leaves the line in place and is measured exactly`() {
        // (0, 5.5), (1, 4.5), (2, 4.5), (3, 5.5): centroid (1.5, 5),
        // Sxy = (−1.5)(.5) + (−.5)(−.5) + (.5)(−.5) + (1.5)(.5) = 0, Sxx = 5 > Syy = 1 → y = 5.
        // Every residual is 0.5 → RMS 0.5, max 0.5.
        val f = fitted(pts(0.0 to 5.5, 1.0 to 4.5, 2.0 to 4.5, 3.0 to 5.5))
        assertLine(0.0, 1.0, -5.0, f.line)
        assertEquals(0.5, f.rmsResidualPx, 1e-15)
        assertEquals(0.5, f.maxAbsResidualPx, 1e-15)
    }

    @Test
    fun `rms and max residual differ when residuals are unequal`() {
        // x = 0..4, y − 5 = [−1, 1, 0, 1, −1]: Σdy = 0, Sxy = 2 − 1 + 0 + 1 − 2 = 0, Sxx = 10, Syy = 4
        // → y = 5. Residuals 1, 1, 0, 1, 1 → RMS √(4/5), max 1.
        val f = fitted(pts(0.0 to 4.0, 1.0 to 6.0, 2.0 to 5.0, 3.0 to 6.0, 4.0 to 4.0))
        assertLine(0.0, 1.0, -5.0, f.line)
        assertEquals(sqrt(0.8), f.rmsResidualPx, 1e-15)
        assertEquals(1.0, f.maxAbsResidualPx, 1e-15)
        // (0, 0), (10, 0), (5, 3): centroid (5, 1), Sxx = 50, Syy = 6, Sxy = 0 → y = 1.
        // Residuals 1, 1, 2 → RMS √(6/3) = √2, max 2.
        val g = fitted(pts(0.0 to 0.0, 10.0 to 0.0, 5.0 to 3.0))
        assertLine(0.0, 1.0, -1.0, g.line)
        assertEquals(r2, g.rmsResidualPx, 1e-15)
        assertEquals(2.0, g.maxAbsResidualPx, 1e-15)
    }

    @Test
    fun `repeated points mixed with distinct ones still fit`() {
        val f = fitted(pts(0.0 to 0.0, 0.0 to 0.0, 0.0 to 0.0, 10.0 to 10.0, 5.0 to 5.0, 5.0 to 5.0))
        assertLine(1 / r2, -1 / r2, 0.0, f.line)
        assertEquals(6, f.pointCount)
        assertEquals(0.0, f.maxAbsResidualPx, 1e-14)
    }

    @Test
    fun `too few points are rejected`() {
        assertRejected(LineFitRejection.TOO_FEW_POINTS, emptyList())
        assertRejected(LineFitRejection.TOO_FEW_POINTS, pts(3.0 to 4.0))
    }

    @Test
    fun `identical points are rejected even when their mean does not round back exactly`() {
        // 0.1 + 0.1 + 0.1 = 0.30000000000000004, / 3 ≠ 0.1: a centroid test would see spread.
        assertRejected(LineFitRejection.IDENTICAL_POINTS, pts(0.1 to 0.2, 0.1 to 0.2, 0.1 to 0.2))
        assertRejected(LineFitRejection.IDENTICAL_POINTS, pts(7.0 to 7.0, 7.0 to 7.0))
        // −0.0 and 0.0 are the same point.
        assertRejected(LineFitRejection.IDENTICAL_POINTS, pts(0.0 to 1.0, -0.0 to 1.0))
    }

    @Test
    fun `isotropic scatter has no direction`() {
        // Square corners: Sxx = Syy = 1, Sxy = 0. A plus shape: Sxx = Syy = 2, Sxy = 0.
        assertRejected(LineFitRejection.NO_DOMINANT_DIRECTION, pts(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0))
        assertRejected(LineFitRejection.NO_DOMINANT_DIRECTION, pts(-1.0 to 0.0, 1.0 to 0.0, 0.0 to -1.0, 0.0 to 1.0))
    }

    @Test
    fun `non-finite input is rejected`() {
        assertRejected(LineFitRejection.NON_FINITE_POINT, pts(0.0 to 0.0, Double.NaN to 1.0, 2.0 to 2.0))
        assertRejected(LineFitRejection.NON_FINITE_POINT, pts(0.0 to 0.0, 1.0 to Double.POSITIVE_INFINITY))
    }

    @Test
    fun `huge spread is rescaled instead of overflowing`() {
        // (±1e200)² would overflow. Through (1e200, 0) and (−1e200, 1): normal ∝ (−1, −2e200),
        // centroid (0, 0.5) → canonical (5e-201, 1, −0.5).
        val f = fitted(pts(1e200 to 0.0, -1e200 to 1.0))
        assertLine(5e-201, 1.0, -0.5, f.line)
        // y = x with points at ±5.9e153: an unscaled direction would overflow the normalisation.
        val d = fitted(pts(-5.9e153 to -5.9e153, 5.9e153 to 5.9e153))
        assertLine(1 / r2, -1 / r2, 0.0, d.line)
    }

    @Test
    fun `large offsets with a large spread fit without overflowing the line offset`() {
        // y = x + 2^480 (≈ 3.1e144) sampled at x = 2^480 + k·2^464, all exactly representable so the
        // points are exactly collinear. An unscaled direction (≈ spread² ≈ 1e279) times the
        // centroid (≈ 1e145) would overflow. Canonical (1/√2, −1/√2, 2^480/√2).
        val big = Math.scalb(1.0, 480)
        val step = Math.scalb(1.0, 464)
        val f = fitted((-2..2).map { PixelPoint(big + it * step, 2 * big + it * step) })
        assertLine(1 / r2, -1 / r2, big / r2, f.line)
    }

    @Test
    fun `tiny separations are rescaled instead of underflowing`() {
        // (1e-200)² underflows to 0; two distinct points must still fit. y = 0 → (0, 1, 0).
        val h = fitted(pts(0.0 to 0.0, 1e-200 to 0.0))
        assertLine(0.0, 1.0, 0.0, h.line)
        // y = x → (1/√2, −1/√2, 0).
        assertLine(1 / r2, -1 / r2, 0.0, fitted(pts(0.0 to 0.0, 1e-200 to 1e-200)).line)
    }

    @Test
    fun `shared coordinates that do not average back exactly still give exact axis lines`() {
        // Mean of three 0.1s is 0.10000000000000002; the shared coordinate is used as is.
        val h = fitted(pts(0.1 to 0.1, 0.2 to 0.1, 0.7 to 0.1))
        assertEquals(0.0, h.line.a, 0.0)
        assertEquals(1.0, h.line.b, 0.0)
        assertEquals(-0.1, h.line.c, 1e-16)
        assertEquals(0.0, h.maxAbsResidualPx, 0.0)
        val v = fitted(pts(0.1 to 0.1, 0.1 to 0.2, 0.1 to 0.7))
        assertEquals(1.0, v.line.a, 0.0)
        assertEquals(0.0, v.line.b, 0.0)
        assertEquals(-0.1, v.line.c, 1e-16)
        assertEquals(0.0, v.maxAbsResidualPx, 0.0)
    }

    @Test
    fun `centroid overflow or an out-of-range line is rejected rather than returning NaN`() {
        // 1.7e308 + 1.6e308 overflows the coordinate sum.
        assertRejected(LineFitRejection.NUMERIC_OVERFLOW, pts(1.7e308 to 0.0, 1.6e308 to 1.0))
        // x = 1e160 is a valid direction but |c| = 1e160 > Line.MAX_ABS_OFFSET.
        assertRejected(LineFitRejection.NUMERIC_OVERFLOW, pts(1e160 to 0.0, 1e160 to 1.0))
    }
}
