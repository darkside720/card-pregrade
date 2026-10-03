package com.cardpregrade.core.cv.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** Expected coefficients and points are derived by hand in the comments; y is down. */
class LineTest {

    private val r2 = sqrt(2.0)

    private fun line(p: Pair<Double, Double>, q: Pair<Double, Double>): Line =
        Line.through(PixelPoint(p.first, p.second), PixelPoint(q.first, q.second)) ?: fail("No line through $p, $q") as Nothing

    private fun assertCoefficients(a: Double, b: Double, c: Double, line: Line, tolerance: Double = 1e-15) {
        assertEquals("a of $line", a, line.a, tolerance)
        assertEquals("b of $line", b, line.b, tolerance)
        assertEquals("c of $line", c, line.c, tolerance * maxOf(1.0, abs(c)))
    }

    private fun assertPoint(x: Double, y: Double, result: LineIntersection, tolerance: Double) {
        if (result !is LineIntersection.Point) fail("Expected a point, got $result") else {
            assertEquals("x", x, result.point.x, tolerance)
            assertEquals("y", y, result.point.y, tolerance)
        }
    }

    @Test
    fun `horizontal line has a downward unit normal`() {
        // Through (0, 5) and (10, 5): normal (0, 10), offset −50 → (0, 1, −5), i.e. y = 5.
        val l = line(0.0 to 5.0, 10.0 to 5.0)
        assertCoefficients(0.0, 1.0, -5.0, l)
        assertEquals(3.0, l.signedDistance(PixelPoint(3.0, 8.0)), 0.0)
        assertEquals(-3.0, l.signedDistance(PixelPoint(3.0, 2.0)), 0.0)
    }

    @Test
    fun `vertical line has a rightward unit normal`() {
        // Through (4, 0) and (4, 9): normal (−9, 0) → canonical (1, 0, −4), i.e. x = 4.
        val l = line(4.0 to 0.0, 4.0 to 9.0)
        assertCoefficients(1.0, 0.0, -4.0, l)
        assertEquals(3.0, l.signedDistance(PixelPoint(7.0, 1.0)), 0.0)
        assertEquals(-3.0, l.signedDistance(PixelPoint(1.0, 100.0)), 0.0)
    }

    @Test
    fun `oblique lines of both slopes`() {
        // y = x through (0,0),(1,1): normal (−1, 1) → canonical (1/√2, −1/√2, 0).
        val rising = line(0.0 to 0.0, 1.0 to 1.0)
        assertCoefficients(1 / r2, -1 / r2, 0.0, rising)
        assertEquals(r2, rising.signedDistance(PixelPoint(2.0, 0.0)), 1e-15)
        assertEquals(-r2, rising.signedDistance(PixelPoint(0.0, 2.0)), 1e-15)
        // x + y = 4 through (0,4),(4,0): normal (4, 4), midpoint (2, 2), c = −16 → (1/√2, 1/√2, −2√2).
        val falling = line(0.0 to 4.0, 4.0 to 0.0)
        assertCoefficients(1 / r2, 1 / r2, -2 * r2, falling)
        assertEquals(2 * r2, falling.signedDistance(PixelPoint(4.0, 4.0)), 1e-14)
    }

    @Test
    fun `coefficients are normalised and scaled copies are equal`() {
        // 3x + 4y − 10 = 0 → |(3, 4)| = 5 → (0.6, 0.8, −2).
        val l = Line.of(3.0, 4.0, -10.0)!!
        assertCoefficients(0.6, 0.8, -2.0, l)
        // Scaled and negated copies: same hand-derived coefficients, and equal as values.
        assertCoefficients(0.6, 0.8, -2.0, Line.of(6.0, 8.0, -20.0)!!)
        assertCoefficients(0.6, 0.8, -2.0, Line.of(-3.0, -4.0, 10.0)!!)
        assertEquals(l, Line.of(6.0, 8.0, -20.0))
        assertEquals(l, Line.of(-3.0, -4.0, 10.0))
        for ((a, b) in listOf(1.0 to 2.0, -7.5 to 0.3, 1e-6 to 3e5, 123.0 to -456.0)) {
            val n = Line.of(a, b, 1.0)!!
            assertEquals(1.0, n.a * n.a + n.b * n.b, 1e-15)
        }
    }

    @Test
    fun `canonical sign makes a positive or a zero and b positive`() {
        assertCoefficients(1.0, 0.0, -5.0, Line.of(-1.0, 0.0, 5.0)!!)
        assertCoefficients(0.0, 1.0, -3.0, Line.of(0.0, -2.0, 6.0)!!)
        val l = Line.of(-2.0, 7.0, 1.0)!!
        assertTrue(l.a > 0.0)
        // −0.0 is stored as +0.0: (−0.0, 3, 3) and (−0.0, −3, −3) are both y = −1 → (0, 1, 1).
        val negZero = Line.of(-0.0, 3.0, 3.0)!!
        assertCoefficients(0.0, 1.0, 1.0, negZero)
        assertEquals(Double.POSITIVE_INFINITY, 1.0 / negZero.a, 0.0)
        val flipped = Line.of(-0.0, -3.0, -3.0)!!
        assertCoefficients(0.0, 1.0, 1.0, flipped)
        assertEquals(Double.POSITIVE_INFINITY, 1.0 / flipped.a, 0.0)
        assertEquals(negZero, flipped)
    }

    @Test
    fun `canonical sign is decided after normalisation so an underflowed a cannot leave b negative`() {
        // (1e-320, −1e10, 5): a / 1e10 = 1e-330 underflows to 0, b → −1, c → 5e-10.
        // Canonical form of (0, −1, 5e-10) is (0, 1, −5e-10).
        val l = Line.of(1e-320, -1e10, 5.0)!!
        assertEquals(0.0, l.a, 0.0)
        assertEquals(1.0, l.b, 0.0)
        assertEquals(-5e-10, l.c, 1e-25)
    }

    @Test
    fun `reversed point order gives an identical line`() {
        val pairs = listOf(
            (0.0 to 5.0) to (10.0 to 5.0),
            (4.0 to 0.0) to (4.0 to 9.0),
            (0.1 to 0.7) to (13.3 to -2.9),
            (1234.5 to 2987.25) to (1301.125 to 3999.0),
        )
        for ((p, q) in pairs) {
            val forward = line(p, q)
            val reverse = line(q, p)
            assertEquals(forward, reverse)
            assertEquals(forward.hashCode(), reverse.hashCode())
        }
    }

    @Test
    fun `points on the line are at zero distance`() {
        val l = line(0.0 to 5.0, 10.0 to 5.0)
        assertEquals(0.0, l.distance(PixelPoint(-1234.0, 5.0)), 0.0)
        val v = Line.of(3.0, 4.0, -10.0)!!
        // (2, 1): 3·2 + 4·1 − 10 = 0.
        assertEquals(0.0, v.distance(PixelPoint(2.0, 1.0)), 1e-15)
        // (5, 5): (15 + 20 − 10) / 5 = 5, on the positive side.
        assertEquals(5.0, v.signedDistance(PixelPoint(5.0, 5.0)), 1e-14)
        assertEquals(5.0, Line.of(-3.0, -4.0, 10.0)!!.distance(PixelPoint(5.0, 5.0)), 1e-14)
    }

    @Test
    fun `horizontal meets vertical`() {
        val h = line(0.0 to 5.0, 10.0 to 5.0)
        val v = line(4.0 to 0.0, 4.0 to 9.0)
        assertPoint(4.0, 5.0, h.intersection(v), 0.0)
        assertPoint(4.0, 5.0, v.intersection(h), 0.0)
    }

    @Test
    fun `oblique lines meet where both equations hold`() {
        // y = x and x + y = 4 → (2, 2).
        assertPoint(2.0, 2.0, line(0.0 to 0.0, 1.0 to 1.0).intersection(line(0.0 to 4.0, 4.0 to 0.0)), 1e-14)
        // 3x + 4y = 10 and 4x − 3y = 5 → (2, 1).
        assertPoint(2.0, 1.0, Line.of(3.0, 4.0, -10.0)!!.intersection(Line.of(4.0, -3.0, -5.0)!!), 1e-14)
    }

    @Test
    fun `intersection is accurate in translated coordinates`() {
        // y = x + 1000 and x + y = 3100 → 2x + 1000 = 3100 → (1050, 2050).
        val a = line(1000.0 to 2000.0, 1001.0 to 2001.0)
        val b = line(1100.0 to 2000.0, 2000.0 to 1100.0)
        assertPoint(1050.0, 2050.0, a.intersection(b), 1e-9)
        assertPoint(1050.0, 2050.0, b.intersection(a), 1e-9)
    }

    @Test
    fun `parallel lines report their separation`() {
        assertEquals(LineIntersection.Parallel(3.0), line(0.0 to 5.0, 10.0 to 5.0).intersection(line(0.0 to 8.0, 1.0 to 8.0)))
        // Near-horizontal lines whose canonical normals point opposite ways:
        // (1e-12, 1, −5) and (−1e-12, 1, −8) → canonical (1e-12, −1, 8). |det| ≈ 2e-12 → parallel;
        // the offsets are compared with the second normal flipped back: |−5 − (−8)| = 3.
        val p = Line.of(1e-12, 1.0, -5.0)!!
        val q = Line.of(-1e-12, 1.0, -8.0)!!
        assertTrue(q.b < 0.0)
        val result = p.intersection(q)
        if (result !is LineIntersection.Parallel) fail("Expected parallel, got $result") else assertEquals(3.0, result.separation, 1e-12)
    }

    @Test
    fun `coincident lines built from different points are detected`() {
        // y = 2x + 7 through two different point pairs.
        val a = line(0.0 to 7.0, 1.0 to 9.0)
        val b = line(10.0 to 27.0, -4.0 to -1.0)
        assertEquals(LineIntersection.Coincident, a.intersection(b))
        assertEquals(LineIntersection.Coincident, b.intersection(a))
        assertEquals(LineIntersection.Coincident, a.intersection(a))
    }

    @Test
    fun `near-parallel guard applies at the documented sine`() {
        // sin θ ≈ 1e-10 ≤ 1e-9: no point is manufactured; y = 8 vs ~y = 5 are 3 apart.
        val shallow = Line.of(1e-10, 1.0, -5.0)!!
        val flat = Line.of(0.0, 1.0, -8.0)!!
        val guarded = shallow.intersection(flat)
        if (guarded !is LineIntersection.Parallel) fail("Expected parallel, got $guarded") else assertEquals(3.0, guarded.separation, 1e-9)
        // sin θ ≈ 1e-8 > 1e-9: a far but well-conditioned point. 1e-8·x + 8 = 5 → x = −3e8.
        val steeper = Line.of(1e-8, 1.0, -5.0)!!
        assertPoint(-3e8, 8.0, steeper.intersection(flat), 1e-6 * 3e8)
    }

    @Test
    fun `classification at the parallel threshold is inclusive and order independent`() {
        // (k, 1, −5) against y = 8: after normalisation a = k exactly (hypot(k, 1) rounds to 1),
        // so det = ±k. Crossing at k·x + 8 = 5 → x = −3 / k.
        val flat = Line.of(0.0, 1.0, -8.0)!!
        for (k in listOf(0.999999e-9, 1e-9)) {
            val l = Line.of(k, 1.0, -5.0)!!
            assertEquals(k, l.a, 0.0)
            for (r in listOf(l.intersection(flat), flat.intersection(l))) {
                if (r !is LineIntersection.Parallel) fail("k = $k: expected parallel, got $r") else assertEquals(3.0, r.separation, 1e-12)
            }
        }
        val just = Line.of(1.000001e-9, 1.0, -5.0)!!
        assertPoint(-3.0 / 1.000001e-9, 8.0, just.intersection(flat), 1e-6)
        assertEquals(just.intersection(flat), flat.intersection(just))
    }

    @Test
    fun `nearly parallel lines crossing near the origin are coincident below the threshold and a point above it`() {
        // Both pass through (0, 5). At k ≤ 1e-9 their offsets agree, so they are one line to
        // numerical precision; just above, the crossing (0, 5) is recovered exactly.
        val flat = Line.of(0.0, 1.0, -5.0)!!
        assertEquals(LineIntersection.Coincident, Line.of(0.999999e-9, 1.0, -5.0)!!.intersection(flat))
        val above = Line.of(1.000001e-9, 1.0, -5.0)!!
        assertPoint(0.0, 5.0, above.intersection(flat), 1e-12)
    }

    @Test
    fun `swapped arguments give identical results including the sign of zero`() {
        // Crossing at exactly x = 0: one order divides +0.0 by det, the other by −det.
        val a = Line.of(2e-9, 1.0, -5.0)!!
        val b = Line.of(0.0, 1.0, -5.0)!!
        val ab = a.intersection(b)
        val ba = b.intersection(a)
        assertEquals(ab, ba)
        if (ab !is LineIntersection.Point) fail("Expected a point, got $ab") else assertEquals(Double.POSITIVE_INFINITY, 1.0 / ab.point.x, 0.0)
    }

    @Test
    fun `results stay finite at the offset bound`() {
        // 1e-8·x + y − 1e150 = 0 and y + 1e150 = 0: y = −1e150, 1e-8·x = 2e150 → x = 2e158, finite.
        val shallow = Line.of(1e-8, 1.0, -1e150)!!
        val flat = Line.of(0.0, 1.0, 1e150)!!
        val hit = shallow.intersection(flat)
        if (hit !is LineIntersection.Point) fail("Expected a point, got $hit") else {
            assertTrue(hit.point.x.isFinite() && hit.point.y.isFinite())
            assertEquals(2e158, hit.point.x, 1e146)
            assertEquals(-1e150, hit.point.y, 1e138)
        }
        // Antiparallel normals at ±1e150: separation 2e150, finite.
        val far = Line.of(0.0, -1.0, -1e150)!!
        assertEquals(LineIntersection.Parallel(2e150), Line.of(0.0, 1.0, -1e150)!!.intersection(far))
    }

    @Test
    fun `coincidence band is relative to the offset as documented`() {
        // |c| = 1e4: band 1e-5 px, so 1e-4 px apart is parallel.
        val r = Line.of(0.0, 1.0, -1e4)!!.intersection(Line.of(0.0, 1.0, -1e4 - 1e-4)!!)
        if (r !is LineIntersection.Parallel) fail("Expected parallel, got $r") else assertEquals(1e-4, r.separation, 1e-9)
        // |c| = 1e12: band 1000 px, so 999 px apart counts as coincident.
        assertEquals(LineIntersection.Coincident, Line.of(0.0, 1.0, -1e12)!!.intersection(Line.of(0.0, 1.0, -1e12 + 999)!!))
    }

    @Test
    fun `degenerate and non-finite input is rejected`() {
        assertNull(Line.through(PixelPoint(3.0, 4.0), PixelPoint(3.0, 4.0)))
        assertNull(Line.through(PixelPoint(Double.NaN, 0.0), PixelPoint(1.0, 1.0)))
        assertNull(Line.through(PixelPoint(0.0, 0.0), PixelPoint(Double.POSITIVE_INFINITY, 1.0)))
        assertNull(Line.of(0.0, 0.0, 1.0))
        assertNull(Line.of(Double.NaN, 1.0, 0.0))
        assertNull(Line.of(1.0, Double.NEGATIVE_INFINITY, 0.0))
        assertNull(Line.of(1.0, 0.0, Double.NaN))
        // Normalised offset 1e300 / 1e-300 overflows.
        assertNull(Line.of(1e-300, 0.0, 1e300))
        // Offsets and coordinates are bounded by MAX_ABS_OFFSET = 1e150.
        assertNull(Line.of(1.0, 0.0, 2e150))
        assertCoefficients(1.0, 0.0, 1e150, Line.of(1.0, 0.0, 1e150)!!)
        assertNull(Line.through(PixelPoint(2e150, 0.0), PixelPoint(0.0, 1.0)))
        assertNull(Line.through(PixelPoint(0.0, 0.0), PixelPoint(1.0, -2e150)))
    }

    @Test
    fun `points at the coordinate bound still give an exact line`() {
        // (1e150, 0) and (−1e150, 1): normal (−1, −2e150), midpoint (0, 0.5), c = 1e150
        // → ÷ 2e150 and flip → (5e-151, 1, −0.5): essentially y = 0.5.
        val l = Line.through(PixelPoint(1e150, 0.0), PixelPoint(-1e150, 1.0))!!
        assertCoefficients(5e-151, 1.0, -0.5, l)
    }

    @Test
    fun `large finite coefficients normalise without overflow`() {
        // hypot(1e300, 1e300) and even |(a, b)| for a = b = 1.5e308 would overflow without pre-scaling.
        assertCoefficients(1 / r2, 1 / r2, 0.0, Line.of(1e300, 1e300, 0.0)!!)
        assertCoefficients(1 / r2, -1 / r2, 0.0, Line.of(-1e300, 1e300, 0.0)!!)
        assertCoefficients(1 / r2, 1 / r2, 0.0, Line.of(1.5e308, 1.5e308, 0.0)!!)
        assertCoefficients(1 / r2, 1 / r2, 0.0, Line.of(Double.MAX_VALUE, Double.MAX_VALUE, 0.0)!!)
    }

    @Test
    fun `subnormal coefficients normalise to a true unit normal`() {
        // MIN_VALUE·√2 would round back to MIN_VALUE, leaving a² + b² = 2 if the norm were formed first.
        assertCoefficients(1 / r2, 1 / r2, 0.0, Line.of(Double.MIN_VALUE, Double.MIN_VALUE, 0.0)!!)
        assertCoefficients(1 / r2, -1 / r2, 0.0, Line.of(Double.MIN_VALUE, -Double.MIN_VALUE, 0.0)!!)
    }
}
