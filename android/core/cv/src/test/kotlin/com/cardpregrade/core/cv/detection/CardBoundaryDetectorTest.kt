package com.cardpregrade.core.cv.detection

import com.cardpregrade.core.cv.geometry.PixelPoint
import com.cardpregrade.core.cv.geometry.QuadRejection
import com.cardpregrade.core.cv.image.GrayImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Hand-built images whose boundaries fall on pixel corners. A 3 × 3 Sobel step between pixels
 * k − 1 and k is reported at exactly k, and exactly vertical/horizontal point sets fit exactly, so
 * the expected corners are the rectangle's own corners.
 */
class CardBoundaryDetectorTest {

    private fun image(w: Int, h: Int, f: (x: Int, y: Int) -> Int) = GrayImage(w, h, IntArray(w * h) { f(it % w, it / w) })

    /** 200 × 300 image, card [40, 160) × [50, 250). */
    private fun card(background: Int, cardLuma: Int, extra: (x: Int, y: Int) -> Int? = { _, _ -> null }) =
        image(200, 300) { x, y -> extra(x, y) ?: if (x in 40 until 160 && y in 50 until 250) cardLuma else background }

    private fun detected(result: BoundaryDetection): BoundaryDetection.Detected =
        result as? BoundaryDetection.Detected ?: fail("Not detected: $result") as Nothing

    private fun assertCorners(expected: List<Pair<Double, Double>>, d: BoundaryDetection.Detected, tolerance: Double = 1e-9) {
        val got = d.quad.points
        for (i in 0 until 4) {
            assertEquals("corner $i x", expected[i].first, got[i].x, tolerance)
            assertEquals("corner $i y", expected[i].second, got[i].y, tolerance)
        }
    }

    private val rectCorners = listOf(40.0 to 50.0, 160.0 to 50.0, 160.0 to 250.0, 40.0 to 250.0)

    @Test
    fun `scan plan takes the centres of equal bands over the Sobel interior`() {
        // n = 8, k = 4: 1 + ⌊(2i + 1)·8 / 8⌋ = 2, 4, 6, 8.
        assertEquals(listOf(2, 4, 6, 8), ScanPlan.lines(10, 4))
        // n = 3 < 10: k = 3 → 1 + ⌊1·3/6⌋, 1 + ⌊3·3/6⌋, 1 + ⌊5·3/6⌋ = 1, 2, 3.
        assertEquals(listOf(1, 2, 3), ScanPlan.lines(5, 10))
        assertEquals(listOf(1), ScanPlan.lines(3, 1))
        assertEquals(emptyList<Int>(), ScanPlan.lines(2, 4))
        val many = ScanPlan.lines(1020, 32)
        assertEquals(32, many.size)
        assertTrue(many.zipWithNext().all { (a, b) -> b > a } && many.first() >= 1 && many.last() <= 1018)
    }

    @Test
    fun `bright card on a dark background is found exactly`() {
        val d = detected(CardBoundaryDetector.detect(card(background = 30, cardLuma = 200)))
        assertCorners(rectCorners, d)
        for (fit in d.sides.values) {
            assertEquals(EdgePolarity.INWARD_BRIGHTER, fit.polarity)
            assertEquals(0.0, fit.maxAbsResidualPx, 1e-12)
            assertTrue("${fit.side} inliers ${fit.inliers.size}", fit.inliers.size >= CardDetectionPolicy.DEFAULT.minInliers)
        }
        assertEquals(listOf(Side.LEFT, Side.TOP, Side.RIGHT, Side.BOTTOM), d.sides.keys.toList())
    }

    @Test
    fun `dark card on a bright background is found with the opposite polarity`() {
        val d = detected(CardBoundaryDetector.detect(card(background = 220, cardLuma = 60)))
        assertCorners(rectCorners, d)
        for (fit in d.sides.values) assertEquals(EdgePolarity.INWARD_DARKER, fit.polarity)
    }

    @Test
    fun `outer edge with less support than an inner edge still wins when it clears the threshold`() {
        // As below, but for rows y < 100 the background left of the card is the card's own luma
        // (150), hiding the outer left edge there. The inner line x = 60 is then the best supported
        // (rows 50..249) while x = 40 is seen only on rows 100..249: about 16 of 21 scans, above
        // ⌈0.6 · best⌉, so the outermost rule (not plain best support) must pick it.
        val img = card(background = 120, cardLuma = 150) { x, y ->
            when {
                x in 60 until 140 && y in 50 until 250 -> 20
                x < 40 && y in 50 until 100 -> 150
                else -> null
            }
        }
        val d = detected(CardBoundaryDetector.detect(img))
        val left = d.sides.getValue(Side.LEFT)
        val leftDiag = d.diagnostics.sides.getValue(Side.LEFT)
        assertTrue("outer support ${leftDiag.chosenSupport} < best ${leftDiag.bestSupport}", leftDiag.chosenSupport!! < leftDiag.bestSupport)
        assertEquals(40.0, left.line.distance(PixelPoint(0.0, 0.0)), 1e-9)
        assertCorners(rectCorners, d)
    }

    @Test
    fun `outer edge wins over an equally long but much stronger inner edge`() {
        // Outer step 120 → 150 (|Sobel| 120), inner box x ∈ [60, 140) at luma 20 spanning the same
        // rows (|Sobel| 520 at x = 60 and 140, opposite polarity). Both vertical lines have the same
        // support; the outermost is the card edge.
        val img = card(background = 120, cardLuma = 150) { x, y -> if (x in 60 until 140 && y in 50 until 250) 20 else null }
        val d = detected(CardBoundaryDetector.detect(img))
        assertCorners(rectCorners, d)
        assertEquals(EdgePolarity.INWARD_BRIGHTER, d.sides.getValue(Side.LEFT).polarity)
        assertEquals(EdgePolarity.INWARD_BRIGHTER, d.sides.getValue(Side.RIGHT).polarity)
    }

    @Test
    fun `an outermost straight line with too little support relative to the card edge is ignored`() {
        // Bright 3-row specks at x ∈ [10, 13) centred on 7 of the scan rows that cross the card,
        // kept 20 px clear of its top and bottom edges so column scans cannot mistake a speck's
        // end for those edges. They form a straight, outermost line with support 7 ≥ minInliers (6)
        // but below ⌈0.6 · 21⌉ = 13 for the card's left edge, so only the support threshold rejects them.
        val scanRows = ScanPlan.lines(300, CardDetectionPolicy.DEFAULT.scanLinesPerAxis).filter { it in 70 until 230 }
        val speckRows = scanRows.take(7)
        assertEquals(7, speckRows.size)
        val img = card(background = 30, cardLuma = 200) { x, y -> if (x in 10 until 13 && speckRows.any { y in it - 1..it + 1 }) 255 else null }
        val d = detected(CardBoundaryDetector.detect(img))
        assertCorners(rectCorners, d)
        assertTrue(d.diagnostics.sides.getValue(Side.LEFT).bestSupport >= 13)
    }

    @Test
    fun `blank image has no evidence for the first side`() {
        val r = CardBoundaryDetector.detect(image(100, 100) { _, _ -> 128 })
        assertEquals(DetectionFailure.InsufficientEvidence(Side.LEFT, 0, 6), (r as BoundaryDetection.NotDetected).failure)
        assertEquals(setOf(Side.LEFT), r.diagnostics.sides.keys)
        assertEquals(0, r.diagnostics.sides.getValue(Side.LEFT).poolSize)
    }

    @Test
    fun `a single vertical step satisfies left and right but leaves top unsupported`() {
        // Bright for x ≥ 100: every row sees one candidate at x = 100; no column sees anything.
        val r = CardBoundaryDetector.detect(image(200, 200) { x, _ -> if (x >= 100) 200 else 30 })
        val failure = (r as BoundaryDetection.NotDetected).failure
        assertEquals(DetectionFailure.InsufficientEvidence(Side.TOP, 0, 6), failure)
        assertEquals(setOf(Side.LEFT, Side.TOP), r.diagnostics.sides.keys)
    }

    @Test
    fun `a lone bright quadrant collapses to a degenerate outline`() {
        // Bright for x ≥ 100 and y ≥ 100: left and right both fit x = 100, top and bottom y = 100,
        // so all four corners are (100, 100).
        val r = CardBoundaryDetector.detect(image(200, 200) { x, y -> if (x >= 100 && y >= 100) 200 else 30 })
        val failure = (r as BoundaryDetection.NotDetected).failure as? DetectionFailure.InvalidQuadrilateral
            ?: fail("Expected an invalid quadrilateral, got ${r.failure}") as Nothing
        assertTrue(QuadRejection.DEGENERATE in failure.rejections)
        for (p in failure.quad.points) {
            assertEquals(100.0, p.x, 1e-9)
            assertEquals(100.0, p.y, 1e-9)
        }
    }

    @Test
    fun `images too small for a Sobel response are rejected`() {
        for ((w, h) in listOf(2 to 50, 50 to 2, 1 to 1)) {
            val r = CardBoundaryDetector.detect(image(w, h) { _, _ -> 0 })
            assertEquals(DetectionFailure.ImageTooSmall(w, h), (r as BoundaryDetection.NotDetected).failure)
        }
        // 3 × 3 has one Sobel centre but no evidence.
        assertTrue((CardBoundaryDetector.detect(image(3, 3) { _, _ -> 0 }) as BoundaryDetection.NotDetected).failure is DetectionFailure.InsufficientEvidence)
    }

    @Test
    fun `observations use the documented continuous coordinates`() {
        val d = detected(CardBoundaryDetector.detect(card(background = 30, cardLuma = 200)))
        for (o in d.sides.getValue(Side.LEFT).inliers) assertEquals(PixelPoint(40.0, o.scanCoordinate + 0.5), o.point)
        for (o in d.sides.getValue(Side.TOP).inliers) assertEquals(PixelPoint(o.scanCoordinate + 0.5, 50.0), o.point)
        for (o in d.sides.getValue(Side.RIGHT).inliers) assertEquals(160.0, o.point.x, 0.0)
        for (o in d.sides.getValue(Side.BOTTOM).inliers) assertEquals(250.0, o.point.y, 0.0)
    }
}
