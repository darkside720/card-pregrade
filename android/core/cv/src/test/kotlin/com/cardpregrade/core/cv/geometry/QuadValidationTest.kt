package com.cardpregrade.core.cv.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every threshold here is supplied explicitly by the test: the point is that QuadValidation
 * enforces whatever policy the caller passes, not that these numbers are right for a detector.
 */
class QuadValidationTest {

    private val image = PixelSize(3072, 4080)

    /** Near-frontal card outline: aspect ≈ 0.713, min side ≈ 2230 px, area ≈ 56 % of the frame. */
    private val card = quad(400.0 to 600.0, 2650.0 to 620.0, 2640.0 to 3760.0, 410.0 to 3740.0)

    private val strict = QuadValidationPolicy(
        minAreaFraction = 0.2,
        minSideLengthPx = 500.0,
        boundsTolerancePx = 0.0,
        interiorAngleRangeDeg = 80.0..100.0,
        aspect = AspectExpectation(CardGeometry.STANDARD_ASPECT_RATIO, 0.03),
    )

    private fun only(
        minAreaFraction: Double? = null,
        minSideLengthPx: Double? = null,
        boundsTolerancePx: Double? = null,
        interiorAngleRangeDeg: ClosedFloatingPointRange<Double>? = null,
        aspect: AspectExpectation? = null,
    ) = QuadValidationPolicy(minAreaFraction, minSideLengthPx, boundsTolerancePx, interiorAngleRangeDeg, aspect)

    @Test
    fun `valid convex card outline passes every enabled check and reports its measurements`() {
        val result = QuadValidation.validate(card, image, strict)
        assertTrue(result.rejections.toString(), result.isValid)
        assertEquals(0.56, result.areaFraction, 0.01)
        assertEquals(2230.0, result.minSideLengthPx, 1.0)
        assertEquals(360.0, result.interiorAnglesDeg.sum(), 1e-9)
        assertEquals(0.7134, result.aspectRatio, 0.0005)
        assertTrue(QuadValidation.validate(card, image, QuadValidationPolicy.STRUCTURAL_ONLY).isValid)
    }

    @Test
    fun `concave outline is not convex but is simple and correctly wound`() {
        val concave = quad(0.0 to 0.0, 1000.0 to 0.0, 400.0 to 400.0, 0.0 to 1000.0)
        val result = QuadValidation.validate(concave, image, QuadValidationPolicy.STRUCTURAL_ONLY)
        assertEquals(setOf(QuadRejection.NOT_CONVEX), result.rejections)
        assertTrue(result.interiorAnglesDeg[2] > 180.0)
        assertEquals(360.0, result.interiorAnglesDeg.sum(), 1e-9)
    }

    @Test
    fun `perfectly symmetric bow tie is self intersecting not degenerate`() {
        // Its shoelace area is exactly zero; it must still be reported for what it is.
        val bowTie = quad(0.0 to 0.0, 1000.0 to 1000.0, 1000.0 to 0.0, 0.0 to 1000.0)
        assertEquals(0.0, bowTie.signedArea, 0.0)
        val result = QuadValidation.validate(bowTie, image, QuadValidationPolicy.STRUCTURAL_ONLY)
        assertEquals(setOf(QuadRejection.SELF_INTERSECTING, QuadRejection.NOT_CONVEX), result.rejections)
    }

    @Test
    fun `swapped corners make a self intersecting outline`() {
        val bowTie = Quadrilateral(card.topLeft, card.bottomRight, card.topRight, card.bottomLeft)
        val result = QuadValidation.validate(bowTie, image, QuadValidationPolicy.STRUCTURAL_ONLY)
        assertEquals(setOf(QuadRejection.SELF_INTERSECTING, QuadRejection.NOT_CONVEX), result.rejections)
    }

    @Test
    fun `mirrored corner labels are reported as wrong winding`() {
        val mirrored = Quadrilateral(card.topLeft, card.bottomLeft, card.bottomRight, card.topRight)
        val result = QuadValidation.validate(mirrored, image, QuadValidationPolicy.STRUCTURAL_ONLY)
        assertEquals(setOf(QuadRejection.WRONG_WINDING), result.rejections)
    }

    @Test
    fun `minimum area follows the supplied policy`() {
        assertEquals(setOf(QuadRejection.AREA_TOO_SMALL), QuadValidation.validate(card, image, only(minAreaFraction = 0.6)).rejections)
        assertTrue(QuadValidation.validate(card, image, only(minAreaFraction = 0.5)).isValid)
    }

    @Test
    fun `minimum side length follows the supplied policy`() {
        assertEquals(setOf(QuadRejection.SIDE_TOO_SHORT), QuadValidation.validate(card, image, only(minSideLengthPx = 2300.0)).rejections)
        assertTrue(QuadValidation.validate(card, image, only(minSideLengthPx = 2200.0)).isValid)
    }

    @Test
    fun `bounds follow the supplied tolerance in both directions`() {
        val poking = quad(-3.0 to 600.0, 2650.0 to 620.0, 2640.0 to 3760.0, 410.0 to 3740.0)
        assertEquals(setOf(QuadRejection.OUT_OF_BOUNDS), QuadValidation.validate(poking, image, only(boundsTolerancePx = 2.0)).rejections)
        assertTrue(QuadValidation.validate(poking, image, only(boundsTolerancePx = 5.0)).isValid)
        // A negative tolerance demands a margin inside the frame: the card's TL is only 400 px in.
        assertEquals(setOf(QuadRejection.OUT_OF_BOUNDS), QuadValidation.validate(card, image, only(boundsTolerancePx = -500.0)).rejections)
        assertTrue(QuadValidation.validate(card, image, only(boundsTolerancePx = -300.0)).isValid)
    }

    @Test
    fun `interior angle range follows the supplied policy`() {
        // Trapezoid seen in perspective: angles ≈ 93.8° at the top, 86.2° at the bottom.
        val trapezoid = quad(500.0 to 500.0, 2500.0 to 500.0, 2700.0 to 3500.0, 300.0 to 3500.0)
        assertEquals(93.8, QuadValidation.validate(trapezoid, image, QuadValidationPolicy.STRUCTURAL_ONLY).interiorAnglesDeg[0], 0.1)
        assertEquals(setOf(QuadRejection.ANGLE_OUT_OF_RANGE), QuadValidation.validate(trapezoid, image, only(interiorAngleRangeDeg = 88.0..92.0)).rejections)
        assertTrue(QuadValidation.validate(trapezoid, image, only(interiorAngleRangeDeg = 80.0..100.0)).isValid)
    }

    @Test
    fun `aspect expectation follows the supplied policy`() {
        val topLoaderShape = AspectExpectation(0.75, 0.03) // ≈ 3 × 4 in; the card measures ≈ 0.713
        assertEquals(setOf(QuadRejection.ASPECT_OUT_OF_RANGE), QuadValidation.validate(card, image, only(aspect = topLoaderShape)).rejections)
        assertTrue(QuadValidation.validate(card, image, only(aspect = AspectExpectation(CardGeometry.STANDARD_ASPECT_RATIO, 0.03))).isValid)
    }

    @Test
    fun `degenerate outlines are flagged and undefined checks are skipped`() {
        val repeatedCorner = quad(400.0 to 600.0, 400.0 to 600.0, 2640.0 to 3760.0, 410.0 to 3740.0)
        val result = QuadValidation.validate(repeatedCorner, image, strict)
        assertEquals(setOf(QuadRejection.DEGENERATE, QuadRejection.SIDE_TOO_SHORT), result.rejections)

        val triangle = quad(0.0 to 0.0, 500.0 to 0.0, 1000.0 to 0.0, 0.0 to 1000.0)
        assertEquals(setOf(QuadRejection.DEGENERATE), QuadValidation.validate(triangle, image, QuadValidationPolicy.STRUCTURAL_ONLY).rejections)
    }

    @Test
    fun `non finite corners short circuit every other check`() {
        val broken = quad(Double.NaN to 600.0, 2650.0 to 620.0, 2640.0 to 3760.0, 410.0 to 3740.0)
        val result = QuadValidation.validate(broken, image, strict)
        assertEquals(setOf(QuadRejection.NON_FINITE), result.rejections)
        assertFalse(result.isValid)
    }

    @Test
    fun `reports every failing check at once`() {
        val bad = quad(-50.0 to -50.0, 300.0 to 0.0, 250.0 to 400.0, 0.0 to 300.0)
        val result = QuadValidation.validate(bad, image, strict)
        assertEquals(
            setOf(
                QuadRejection.AREA_TOO_SMALL,
                QuadRejection.SIDE_TOO_SHORT,
                QuadRejection.OUT_OF_BOUNDS,
                QuadRejection.ANGLE_OUT_OF_RANGE,
                QuadRejection.ASPECT_OUT_OF_RANGE,
            ),
            result.rejections,
        )
    }

    @Test
    fun `policy rejects nonsensical thresholds at construction`() {
        assertThrows(IllegalArgumentException::class.java) { only(minAreaFraction = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { only(minAreaFraction = 1.5) }
        assertThrows(IllegalArgumentException::class.java) { only(minSideLengthPx = -1.0) }
        assertThrows(IllegalArgumentException::class.java) { only(interiorAngleRangeDeg = 100.0..80.0) }
        assertThrows(IllegalArgumentException::class.java) { AspectExpectation(0.0, 0.1) }
        assertThrows(IllegalArgumentException::class.java) { AspectExpectation(0.7, -0.1) }
    }

    private fun quad(tl: Pair<Double, Double>, tr: Pair<Double, Double>, br: Pair<Double, Double>, bl: Pair<Double, Double>) =
        Quadrilateral(PixelPoint(tl.first, tl.second), PixelPoint(tr.first, tr.second), PixelPoint(br.first, br.second), PixelPoint(bl.first, bl.second))
}
