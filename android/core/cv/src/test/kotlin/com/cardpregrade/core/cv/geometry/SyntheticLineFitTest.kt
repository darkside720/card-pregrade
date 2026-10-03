package com.cardpregrade.core.cv.geometry

import com.cardpregrade.core.cv.fixtures.SyntheticFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.sqrt

/**
 * Line fitting against the fixture outlines. Points are interpolated directly along each side of
 * [com.cardpregrade.core.cv.fixtures.SyntheticScene.cardQuad] (the same quads the renderer uses);
 * nothing is rendered and EdgeCandidates is not involved, so this checks the fitter and line
 * intersection only, not candidate selection or detection.
 *
 * Sides are fitted from interior points only (fractions 0.15–0.85), so the corners are genuine
 * predictions of the fitted lines rather than inputs.
 */
class SyntheticLineFitTest {

    private val fixtures = listOf("centered", "translated", "rot-cw-20", "rot-ccw-25", "persp-mild", "persp-strong", "combined")
    private val fractions = listOf(0.15, 0.30, 0.50, 0.70, 0.85)

    /** Sides in outline order; side i runs from corner i to corner i + 1 (TL, TR, BR, BL). */
    private fun sides(q: Quadrilateral): List<Pair<PixelPoint, PixelPoint>> =
        q.points.indices.map { q.points[it] to q.points[(it + 1) % 4] }

    private fun along(a: PixelPoint, b: PixelPoint, t: Double) = PixelPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    private fun fit(points: List<PixelPoint>, what: String): LineFitResult.Fitted =
        when (val r = LineFitter.fit(points)) {
            is LineFitResult.Fitted -> r
            is LineFitResult.Rejected -> fail("$what: $r") as Nothing
        }

    private fun assertCornersFromFits(q: Quadrilateral, lines: List<Line>, tolerance: Double, name: String) {
        // Corner i is where side i − 1 (ending at it) meets side i (starting at it).
        for (i in 0 until 4) {
            val hit = lines[(i + 3) % 4].intersection(lines[i])
            if (hit !is LineIntersection.Point) fail("$name corner $i: $hit") else {
                val corner = q.points[i]
                assertTrue("$name corner $i: ${hit.point} vs $corner", hit.point.distanceTo(corner) <= tolerance)
            }
        }
    }

    @Test
    fun `exact side points fit their sides and adjacent fits meet at the corners`() {
        for (name in fixtures) {
            val q = SyntheticFixtures.request(name).cardQuad
            val lines = sides(q).mapIndexed { i, (a, b) ->
                val f = fit(fractions.map { along(a, b, it) }, "$name side $i")
                assertEquals(fractions.size, f.pointCount)
                assertEquals("$name side $i rms", 0.0, f.rmsResidualPx, 1e-9)
                assertEquals("$name side $i max", 0.0, f.maxAbsResidualPx, 1e-9)
                // The side's own end points, which were not fitted, lie on the line.
                assertEquals("$name side $i start", 0.0, f.line.distance(a), 1e-9)
                assertEquals("$name side $i end", 0.0, f.line.distance(b), 1e-9)
                f.line
            }
            assertCornersFromFits(q, lines, 1e-8, name)
        }
    }

    @Test
    fun `axis-aligned fixture produces exactly vertical and horizontal fits`() {
        // "centered" has no rotation or tilt, so its sides are horizontal (top, bottom) and vertical.
        val q = SyntheticFixtures.request("centered").cardQuad
        val lines = sides(q).map { (a, b) -> fit(fractions.map { t -> along(a, b, t) }, "centered").line }
        for (i in listOf(0, 2)) assertEquals(0.0, lines[i].a, 1e-12)
        for (i in listOf(1, 3)) assertEquals(0.0, lines[i].b, 1e-12)
    }

    @Test
    fun `symmetric perpendicular offsets keep each fitted side and report the offsets exactly`() {
        // Each point is pushed off its side along the unit normal by e·[+1, −1, 0, −1, +1] at
        // t = [.15, .30, .50, .70, .85]. The offsets sum to 0 and Σ(t − 0.5)·offset =
        // −.35e + .20e + 0 − .20e + .35e = 0, so in the side's own frame the centroid is on the
        // side and the cross term vanishes: the TLS line is the true side. Residuals are
        // e, e, 0, e, e → RMS e·√(4/5), max e.
        val e = 0.25
        val pattern = listOf(1.0, -1.0, 0.0, -1.0, 1.0)
        for (name in fixtures) {
            val q = SyntheticFixtures.request(name).cardQuad
            val lines = sides(q).mapIndexed { i, (a, b) ->
                val len = a.distanceTo(b)
                val nx = -(b.y - a.y) / len
                val ny = (b.x - a.x) / len
                val points = fractions.indices.map { k ->
                    val p = along(a, b, fractions[k])
                    PixelPoint(p.x + nx * e * pattern[k], p.y + ny * e * pattern[k])
                }
                val f = fit(points, "$name side $i")
                assertEquals("$name side $i rms", e * sqrt(0.8), f.rmsResidualPx, 1e-9)
                assertEquals("$name side $i max", e, f.maxAbsResidualPx, 1e-9)
                assertEquals("$name side $i start", 0.0, f.line.distance(a), 1e-8)
                assertEquals("$name side $i end", 0.0, f.line.distance(b), 1e-8)
                f.line
            }
            assertCornersFromFits(q, lines, 1e-7, name)
        }
    }
}
