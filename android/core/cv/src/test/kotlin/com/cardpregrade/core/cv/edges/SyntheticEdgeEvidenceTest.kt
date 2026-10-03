package com.cardpregrade.core.cv.edges

import com.cardpregrade.core.cv.fixtures.SyntheticFixtures
import com.cardpregrade.core.cv.fixtures.SyntheticScene
import com.cardpregrade.core.cv.geometry.PixelPoint
import com.cardpregrade.core.cv.geometry.Quadrilateral
import com.cardpregrade.core.cv.image.Sobel
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor

/**
 * Sobel + [EdgeCandidates] produce gradient evidence where the card's physical outline crosses a
 * scan line, with the outline taken from [SyntheticScene.cardQuad] (the renderer's independent
 * input, never from the image).
 *
 * The scenes are deterministic, point-sampled, crisp and noise-free. These tests prove the
 * geometry → gradient-evidence chain only; they say nothing about robustness to JPEG noise, blur,
 * glare, shadows, textured backgrounds, or sleeves and top-loaders.
 *
 * They assert that *some* candidate of the right polarity lies near each known boundary, not that
 * it is the outermost or the strongest: the printed frame and art produce further candidates, and
 * choosing the card edge among them is detector policy, not EdgeCandidates' job.
 */
class SyntheticEdgeEvidenceTest {

    /**
     * Card border luma ≈ 203 against a background of 32, so a crisp edge gives |Sobel| up to
     * 4 · 171 = 684. 200 keeps every card-edge crossing (even slanted ones spread over two
     * samples) while dropping the art checkerboard (luma 134 vs 102 → at most 4 · 32 = 128).
     */
    private val minResponse = 200

    /**
     * Along a scan line, a point-sampled step lands on the pixel corner nearest the true
     * crossing (≤ 0.5 px), and the 3-row/column Sobel window averages a slanted edge over its
     * neighbouring lines (weights 1-2-1, symmetric about the scan line, so it adds a sub-pixel
     * quantisation error rather than a bias). 1.0 px bounds both.
     */
    private val tolerancePx = 1.0

    /** Edge fractions sampled along each side, kept well away from the corners. */
    private val fractions = listOf(0.3, 0.5, 0.7)

    private val fixtures = listOf("centered", "translated", "rot-cw-20", "rot-ccw-25", "persp-mild", "persp-strong", "combined")

    @Test
    fun `rows crossing the left edge carry a rising dx candidate there`() = checkSide(Side.LEFT)

    @Test
    fun `rows crossing the right edge carry a falling dx candidate there`() = checkSide(Side.RIGHT)

    @Test
    fun `columns crossing the top edge carry a rising dy candidate there`() = checkSide(Side.TOP)

    @Test
    fun `columns crossing the bottom edge carry a falling dy candidate there`() = checkSide(Side.BOTTOM)

    /**
     * One card side: [start]→[end] picks its corners, [horizontalScan] says whether rows (rowDx)
     * or columns (columnDy) cross it, [rising] is the expected polarity going background → card.
     */
    private enum class Side(val horizontalScan: Boolean, val rising: Boolean) {
        LEFT(true, true), RIGHT(true, false), TOP(false, true), BOTTOM(false, false);

        fun corners(q: Quadrilateral): Pair<PixelPoint, PixelPoint> = when (this) {
            LEFT -> q.topLeft to q.bottomLeft
            RIGHT -> q.topRight to q.bottomRight
            TOP -> q.topLeft to q.topRight
            BOTTOM -> q.bottomLeft to q.bottomRight
        }
    }

    private fun checkSide(side: Side) {
        for (name in fixtures) {
            val scene = SyntheticFixtures.render(name)
            val gray = scene.image.toGrayImage()
            val q = scene.cardQuad
            val (a, b) = side.corners(q)
            for (f in fractions) {
                val what = "$name ${side.name.lowercase()} f=$f"
                val (expected, candidates, span) = if (side.horizontalScan) {
                    val row = floor(a.y + (b.y - a.y) * f).toInt()
                    val y = row + 0.5
                    Triple(
                        crossing(a.y, a.x, b.y, b.x, y, what),
                        EdgeCandidates.find(Sobel.rowDx(gray, row, 1, gray.width - 1), 1, minResponse),
                        span(q, y) { it.y to it.x },
                    )
                } else {
                    val column = floor(a.x + (b.x - a.x) * f).toInt()
                    val x = column + 0.5
                    Triple(
                        crossing(a.x, a.y, b.x, b.y, x, what),
                        EdgeCandidates.find(Sobel.columnDy(gray, column, 1, gray.height - 1), 1, minResponse),
                        span(q, x) { it.x to it.y },
                    )
                }
                val near = candidates.filter { abs(it.position - expected) <= tolerancePx && (it.response > 0) == side.rising }
                assertTrue("$what: no candidate within $tolerancePx px of $expected in $candidates", near.isNotEmpty())
                // The background is flat, so nothing may sit clearly outside the card on this line.
                val stray = candidates.filter { it.position < span.first - tolerancePx || it.position > span.second + tolerancePx }
                assertTrue("$what: candidates outside the card span $span: $stray", stray.isEmpty())
            }
        }
    }

    /**
     * Where the line {along = c} meets the segment from (a0, a1) to (b0, b1), given as
     * (along, across) coordinates; the crossing must lie in the middle 70 % of the segment.
     */
    private fun crossing(a0: Double, a1: Double, b0: Double, b1: Double, c: Double, what: String): Double {
        val t = (c - a0) / (b0 - a0)
        assertTrue("$what: scan line at t = $t is too close to a corner", t in 0.15..0.85)
        return a1 + t * (b1 - a1)
    }

    /** Min and max "across" coordinate where the line {along = c} meets the convex quad's outline. */
    private fun span(q: Quadrilateral, c: Double, coords: (PixelPoint) -> Pair<Double, Double>): Pair<Double, Double> {
        val hits = ArrayList<Double>()
        val p = q.points
        for (i in 0 until 4) {
            val (a0, a1) = coords(p[i])
            val (b0, b1) = coords(p[(i + 1) % 4])
            if (a0 == b0) continue
            val t = (c - a0) / (b0 - a0)
            if (t in 0.0..1.0) hits += a1 + t * (b1 - a1)
        }
        check(hits.size >= 2) { "line $c misses $q" }
        return hits.min() to hits.max()
    }

    @Test
    fun `fixture card corners keep full Sobel support inside the scene`() {
        // Guards the fixture choice: every expected crossing must have full Sobel support and be
        // representable as a non-endpoint candidate (profile spans pixels 1 .. size − 2).
        for (name in fixtures) {
            val scene = SyntheticFixtures.render(name)
            val gray = scene.image.toGrayImage()
            for (p in scene.cardQuad.points) {
                assertTrue("$name corner $p", p.x > 3.0 && p.x < gray.width - 3.0 && p.y > 3.0 && p.y < gray.height - 3.0)
            }
        }
    }
}
