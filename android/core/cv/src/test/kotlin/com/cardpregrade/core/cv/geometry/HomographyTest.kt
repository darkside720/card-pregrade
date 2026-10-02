package com.cardpregrade.core.cv.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

class HomographyTest {

    /** A genuinely projective transform (non-zero h20/h21), with h22 = 1. */
    private val knownElements = listOf(1.15, 0.12, 40.0, -0.06, 0.95, 25.0, 2.0e-4, -1.5e-4, 1.0)
    private val known = solved(Homography.fromRowMajor(knownElements))

    /** A near-frontal card outline in an upright 3072 × 4080 capture. */
    private val card = quad(300.0 to 400.0, 2700.0 to 350.0, 2800.0 to 3700.0, 250.0 to 3650.0)

    @Test
    fun `identity correspondence gives the identity matrix`() {
        val h = solved(Homography.fromFourPoints(card, card))
        assertMatrix(Homography.IDENTITY.toRowMajor(), h.toRowMajor(), 1e-9)
        listOf(PixelPoint(0.0, 0.0), PixelPoint(1234.5, 2345.25), PixelPoint(3072.0, 4080.0)).forEach {
            assertPoint(it, h.apply(it), 1e-9)
        }
        assertEquals(1.0, Homography.IDENTITY.determinant, 0.0)
    }

    @Test
    fun `recovers a known perspective transform from its corner correspondences`() {
        val destination = map(known, card)
        val h = solved(Homography.fromFourPoints(card, destination))

        assertMatrix(knownElements, h.toRowMajor(), 1e-9)
        assertTrue("transform must be projective, not affine", abs(h[2, 0]) > 1e-5 && abs(h[2, 1]) > 1e-5)
        // Interior points (not just the four inputs) must follow the same projective mapping.
        for (fx in listOf(0.1, 0.37, 0.5, 0.82)) for (fy in listOf(0.05, 0.5, 0.66, 0.93)) {
            val p = PixelPoint(300.0 + fx * 2400.0, 400.0 + fy * 3250.0)
            assertPoint(known.apply(p)!!, h.apply(p), 1e-6)
        }
    }

    @Test
    fun `general four point mapping sends every corner to its destination`() {
        val source = quad(10.0 to 20.0, 410.0 to -5.0, 395.0 to 610.0, -15.0 to 580.0)
        val destination = quad(1000.0 to 1000.0, 1800.0 to 1150.0, 1700.0 to 2300.0, 950.0 to 2100.0)
        val h = solved(Homography.fromFourPoints(source, destination))
        source.points.zip(destination.points).forEach { (from, to) -> assertPoint(to, h.apply(from), 1e-9) }
    }

    @Test
    fun `quad to rectangle convenience maps corners and preserves the diagonal intersection`() {
        val size = CardGeometry.correctedSize(1008) // 1008 x 1408, 16 px/mm
        assertEquals(PixelSize(1008, 1408), size)
        val h = solved(Homography.fromQuadToRect(card, size))

        assertPoint(PixelPoint(0.0, 0.0), h.apply(card.topLeft), 1e-9)
        assertPoint(PixelPoint(1008.0, 0.0), h.apply(card.topRight), 1e-9)
        assertPoint(PixelPoint(1008.0, 1408.0), h.apply(card.bottomRight), 1e-9)
        assertPoint(PixelPoint(0.0, 1408.0), h.apply(card.bottomLeft), 1e-9)
        // Projective invariant: the diagonals' intersection maps to the rectangle centre.
        assertPoint(PixelPoint(504.0, 704.0), h.apply(diagonalIntersection(card)), 1e-8)
        // Under perspective the source centroid is generally NOT the image of the centre.
        val centroid = PixelPoint(card.points.sumOf { it.x } / 4, card.points.sumOf { it.y } / 4)
        assertNotEquals(504.0, h.apply(centroid)!!.x, 1e-3)
    }

    @Test
    fun `inverse round trips points and swaps the correspondence`() {
        val destination = map(known, card)
        val h = solved(Homography.fromFourPoints(card, destination))
        val inverse = solved(h.inverse())
        for (x in listOf(250.0, 1000.0, 2222.2, 2800.0)) for (y in listOf(350.0, 1500.5, 3700.0)) {
            val p = PixelPoint(x, y)
            assertPoint(p, inverse.apply(h.apply(p)!!), 1e-7)
        }
        destination.points.zip(card.points).forEach { (from, to) -> assertPoint(to, inverse.apply(from), 1e-7) }
        assertMatrix(solved(Homography.fromFourPoints(destination, card)).toRowMajor(), inverse.toRowMajor(), 1e-9)
    }

    @Test
    fun `row major construction is scale invariant and exposes copies only`() {
        val scaled = solved(Homography.fromRowMajor(knownElements.map { it * 5.0 }))
        assertMatrix(knownElements, scaled.toRowMajor(), 1e-12)
        val values = known.toRowMajor()
        assertEquals(9, values.size)
        for (r in 0..2) for (c in 0..2) assertEquals(values[r * 3 + c], known[r, c], 0.0)
        val scale = solved(Homography.fromRowMajor(listOf(2.0, 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 0.0, 1.0)))
        assertEquals(4.0, scale.determinant, 1e-12)
    }

    @Test
    fun `repeated points are rejected and attributed to the offending set`() {
        val repeated = quad(0.0 to 0.0, 0.0 to 0.0, 100.0 to 100.0, 0.0 to 100.0)
        assertFailure(Homography.fromFourPoints(repeated, card), HomographyFailure.REPEATED_POINTS, "source")
        assertFailure(Homography.fromFourPoints(card, repeated), HomographyFailure.REPEATED_POINTS, "destination")
    }

    @Test
    fun `collinear points are rejected`() {
        val collinear = quad(0.0 to 0.0, 100.0 to 0.0, 200.0 to 0.0, 50.0 to 80.0)
        assertFailure(Homography.fromFourPoints(collinear, card), HomographyFailure.COLLINEAR_POINTS, "source")
    }

    @Test
    fun `near collinear points are rejected but a thin valid quad is solved`() {
        val nearlyCollinear = quad(0.0 to 0.0, 100.0 to 0.0, 200.0 to 1e-8, 50.0 to 80.0)
        assertFailure(Homography.fromFourPoints(nearlyCollinear, card), HomographyFailure.COLLINEAR_POINTS, "source")

        val thin = quad(0.0 to 0.0, 2000.0 to 0.0, 2000.0 to 5.0, 0.0 to 5.0)
        val h = solved(Homography.fromFourPoints(thin, card))
        thin.points.zip(card.points).forEach { (from, to) -> assertPoint(to, h.apply(from), 1e-6) }
    }

    @Test
    fun `non finite input is rejected`() {
        val withNaN = quad(Double.NaN to 0.0, 100.0 to 0.0, 100.0 to 100.0, 0.0 to 100.0)
        assertFailure(Homography.fromFourPoints(withNaN, card), HomographyFailure.NON_FINITE_INPUT, "source")
        val withInfinity = quad(0.0 to 0.0, Double.POSITIVE_INFINITY to 0.0, 100.0 to 100.0, 0.0 to 100.0)
        assertFailure(Homography.fromFourPoints(card, withInfinity), HomographyFailure.NON_FINITE_INPUT, "destination")
        assertFailure(
            Homography.fromRowMajor(listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, Double.NaN, 1.0)),
            HomographyFailure.NON_FINITE_INPUT,
        )
    }

    @Test
    fun `singular and near singular matrices are not invertible`() {
        val rankTwo = listOf(1.0, 2.0, 3.0, 2.0, 4.0, 6.0, 0.0, 0.0, 1.0)
        assertFailure(Homography.fromRowMajor(rankTwo), HomographyFailure.NOT_INVERTIBLE)
        val nearlySingular = listOf(1.0, 2.0, 3.0, 2.0, 4.0 + 1e-14, 6.0, 0.0, 0.0, 1.0)
        assertFailure(Homography.fromRowMajor(nearlySingular), HomographyFailure.NOT_INVERTIBLE)
        assertFailure(Homography.fromRowMajor(List(9) { 0.0 }), HomographyFailure.NOT_INVERTIBLE)
    }

    @Test
    fun `transform sending the source centroid to infinity is reported as a singular system`() {
        // (x, y) -> (1/x, y/x) is a valid invertible homography, but its h22 is 0, which the
        // h22 = 1 parametrization cannot represent. It must fail cleanly, not return garbage.
        val swap = solved(Homography.fromRowMajor(listOf(0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0)))
        val source = quad(-1.0 to -1.0, 1.0 to -1.0, 1.0 to 1.0, -1.0 to 1.0)
        val destination = map(swap, source)
        assertFailure(Homography.fromFourPoints(source, destination), HomographyFailure.SINGULAR_SYSTEM)
    }

    @Test
    fun `points on the line sent to infinity project to null`() {
        val swap = solved(Homography.fromRowMajor(listOf(0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0)))
        assertNull(swap.apply(PixelPoint(0.0, 5.0)))
        assertPoint(PixelPoint(0.5, 2.5), swap.apply(PixelPoint(2.0, 5.0)), 1e-12)
    }

    @Test
    fun `apply matches a hand computed projection`() {
        // w = 2e-4·1000 − 1.5e-4·2000 + 1 = 0.9
        // x = (1.15·1000 + 0.12·2000 + 40) / 0.9 = 1430 / 0.9
        // y = (−0.06·1000 + 0.95·2000 + 25) / 0.9 = 1865 / 0.9
        assertPoint(PixelPoint(14300.0 / 9.0, 18650.0 / 9.0), known.apply(PixelPoint(1000.0, 2000.0)), 1e-9)
    }

    @Test
    fun `solves an analytically known strongly projective transform`() {
        // (x, y) -> (x, y) / (1 + x/1000): matrix [1 0 0; 0 1 0; 0.001 0 1]. The square's right
        // edge (w = 2) shrinks to half height, a trapezoid with 2:1 perspective.
        val square = quad(0.0 to 0.0, 1000.0 to 0.0, 1000.0 to 1000.0, 0.0 to 1000.0)
        val trapezoid = quad(0.0 to 0.0, 500.0 to 0.0, 500.0 to 500.0, 0.0 to 1000.0)
        val h = solved(Homography.fromFourPoints(square, trapezoid))
        assertMatrix(listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.001, 0.0, 1.0), h.toRowMajor(), 1e-12)
        assertPoint(PixelPoint(1000.0 / 3.0, 1000.0 / 3.0), h.apply(PixelPoint(500.0, 500.0)), 1e-9)
    }

    @Test
    fun `pure translations are invertible however far they move the origin`() {
        for (t in listOf(0.0, 10_000.0, 1_000_000.0)) {
            val h = solved(Homography.fromRowMajor(listOf(1.0, 0.0, t, 0.0, 1.0, t, 0.0, 0.0, 1.0)))
            val inverse = solved(h.inverse())
            assertPoint(PixelPoint(-t, -t), inverse.apply(PixelPoint(0.0, 0.0)), 1e-9 * max(1.0, t))
        }
    }

    @Test
    fun `canonical card rectangle maps onto a small card far from the image origin`() {
        // Renderer direction: corrected-card space -> a ≈300 px card near the bottom-right of a 3072 × 4080 frame.
        val canonical = quad(0.0 to 0.0, 1008.0 to 0.0, 1008.0 to 1408.0, 0.0 to 1408.0)
        val smallCard = quad(2700.0 to 3600.0, 3000.0 to 3606.0, 3003.0 to 4019.0, 2697.0 to 4015.0)
        val h = solved(Homography.fromFourPoints(canonical, smallCard))
        assertReprojects(h, canonical, smallCard)
        assertReprojects(solved(h.inverse()), smallCard, canonical)
        assertReprojects(solved(Homography.fromQuadToRect(smallCard, PixelSize(1008, 1408))), smallCard, canonical)
    }

    @Test
    fun `solvability does not depend on translation of either point set`() {
        val source = quad(10.0 to 20.0, 410.0 to -5.0, 395.0 to 610.0, -15.0 to 580.0)
        val destination = quad(100.0 to 100.0, 180.0 to 115.0, 170.0 to 230.0, 95.0 to 210.0)
        for (offset in listOf(0.0, 1e3, 1e4, 1e6)) {
            val shiftedSource = transform(source, 1.0, offset)
            val shiftedDestination = transform(destination, 1.0, offset)
            assertReprojects(solved(Homography.fromFourPoints(source, shiftedDestination)), source, shiftedDestination)
            assertReprojects(solved(Homography.fromFourPoints(shiftedSource, destination)), shiftedSource, destination)
            assertReprojects(solved(Homography.fromFourPoints(shiftedSource, shiftedDestination)), shiftedSource, shiftedDestination)
        }
    }

    @Test
    fun `solvability does not depend on uniform scale`() {
        val source = quad(10.0 to 20.0, 410.0 to -5.0, 395.0 to 610.0, -15.0 to 580.0)
        val destination = quad(100.0 to 100.0, 180.0 to 115.0, 170.0 to 230.0, 95.0 to 210.0)
        for (scale in listOf(1e-6, 1.0, 1e3)) {
            val s = transform(source, scale, 0.0)
            val d = transform(destination, scale, 0.0)
            val h = solved(Homography.fromFourPoints(s, d))
            assertReprojects(h, s, d)
            assertReprojects(solved(h.inverse()), d, s)
        }
    }

    @Test
    fun `seeded random convex quads with the same winding always solve`() {
        val random = Random(20261002)
        fun randomQuad(maxCentre: Double, minSize: Double, sizeRange: Double): Quadrilateral {
            val cx = random.nextDouble() * maxCentre
            val cy = random.nextDouble() * maxCentre
            val w = minSize + random.nextDouble() * sizeRange
            val h = minSize + random.nextDouble() * sizeRange
            fun jitter() = (random.nextDouble() * 2 - 1) * 0.3
            return quad(
                cx - w / 2 + jitter() * w to cy - h / 2 + jitter() * h,
                cx + w / 2 + jitter() * w to cy - h / 2 + jitter() * h,
                cx + w / 2 + jitter() * w to cy + h / 2 + jitter() * h,
                cx - w / 2 + jitter() * w to cy + h / 2 + jitter() * h,
            )
        }
        var pairs = 0
        while (pairs < 2000) {
            val source = randomQuad(4000.0, 200.0, 3000.0)
            val destination = randomQuad(1500.0, 50.0, 1500.0)
            if (!source.isConvex || !destination.isConvex || source.signedArea <= 0 || destination.signedArea <= 0) continue
            pairs++
            val h = solved(Homography.fromFourPoints(source, destination))
            assertReprojects(h, source, destination)
            assertReprojects(solved(h.inverse()), destination, source)
        }
    }

    // --- helpers ---

    private fun transform(q: Quadrilateral, scale: Double, offset: Double): Quadrilateral {
        val p = q.points.map { PixelPoint(it.x * scale + offset, it.y * scale + offset) }
        return Quadrilateral(p[0], p[1], p[2], p[3])
    }

    /** Every corner of [from] lands on [to] within 1e-6 of the destination's extent. */
    private fun assertReprojects(h: Homography, from: Quadrilateral, to: Quadrilateral) {
        val tolerance = 1e-6 * extent(to)
        from.points.zip(to.points).forEach { (s, d) -> assertPoint(d, h.apply(s), tolerance) }
    }

    private fun extent(q: Quadrilateral): Double {
        val p = q.points
        var best = 0.0
        for (i in p.indices) for (j in i + 1 until p.size) best = max(best, p[i].distanceTo(p[j]))
        return best
    }

    private fun quad(tl: Pair<Double, Double>, tr: Pair<Double, Double>, br: Pair<Double, Double>, bl: Pair<Double, Double>) =
        Quadrilateral(PixelPoint(tl.first, tl.second), PixelPoint(tr.first, tr.second), PixelPoint(br.first, br.second), PixelPoint(bl.first, bl.second))

    private fun map(h: Homography, q: Quadrilateral): Quadrilateral {
        val p = q.points.map { h.apply(it)!! }
        return Quadrilateral(p[0], p[1], p[2], p[3])
    }

    private fun diagonalIntersection(q: Quadrilateral): PixelPoint {
        val (a, b) = q.topLeft to q.bottomRight
        val (c, d) = q.topRight to q.bottomLeft
        val denom = (a.x - b.x) * (c.y - d.y) - (a.y - b.y) * (c.x - d.x)
        val t = ((a.x - c.x) * (c.y - d.y) - (a.y - c.y) * (c.x - d.x)) / denom
        return PixelPoint(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
    }

    private fun solved(result: HomographyResult): Homography = when (result) {
        is HomographyResult.Solved -> result.homography
        is HomographyResult.Failed -> fail("Expected a solution, got $result") as Nothing
    }

    private fun assertFailure(result: HomographyResult, reason: HomographyFailure, detail: String? = null) {
        if (result !is HomographyResult.Failed) fail("Expected $reason, got $result")
        result as HomographyResult.Failed
        assertEquals(reason, result.reason)
        if (detail != null) assertEquals(detail, result.detail)
    }

    private fun assertPoint(expected: PixelPoint, actual: PixelPoint?, tolerance: Double) {
        if (actual == null) fail("Expected $expected, got null")
        assertEquals("x", expected.x, actual!!.x, tolerance)
        assertEquals("y", expected.y, actual.y, tolerance)
    }

    /** Element-wise comparison with a tolerance relative to each expected element's magnitude. */
    private fun assertMatrix(expected: List<Double>, actual: List<Double>, relativeTolerance: Double) {
        expected.zip(actual).forEachIndexed { i, (e, a) ->
            assertEquals("element $i", e, a, relativeTolerance * max(1.0, abs(e)))
        }
    }
}
