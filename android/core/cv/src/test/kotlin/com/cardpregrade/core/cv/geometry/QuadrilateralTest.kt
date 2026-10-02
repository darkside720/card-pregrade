package com.cardpregrade.core.cv.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadrilateralTest {

    private val tl = PixelPoint(100.0, 50.0)
    private val tr = PixelPoint(730.0, 60.0)
    private val br = PixelPoint(725.0, 940.0)
    private val bl = PixelPoint(95.0, 930.0)

    @Test
    fun `orders shuffled points as TL TR BR BL`() {
        val quad = Quadrilateral.fromUnordered(listOf(br, tl, bl, tr))
        assertEquals(tl, quad.topLeft)
        assertEquals(tr, quad.topRight)
        assertEquals(br, quad.bottomRight)
        assertEquals(bl, quad.bottomLeft)
    }

    @Test
    fun `area of an axis aligned rectangle`() {
        val quad = Quadrilateral(
            PixelPoint(0.0, 0.0), PixelPoint(630.0, 0.0), PixelPoint(630.0, 880.0), PixelPoint(0.0, 880.0),
        )
        assertEquals(630.0 * 880.0, quad.area, 1e-6)
        assertEquals(CardGeometry.STANDARD_ASPECT_RATIO, quad.aspectRatio, 1e-9)
        assertTrue(quad.isConvex)
    }

    @Test
    fun `self intersecting outline is not convex`() {
        val bowTie = Quadrilateral(
            PixelPoint(0.0, 0.0), PixelPoint(100.0, 100.0), PixelPoint(100.0, 0.0), PixelPoint(0.0, 100.0),
        )
        assertFalse(bowTie.isConvex)
    }

    @Test
    fun `signed area is positive for the TL TR BR BL convention and negative when mirrored`() {
        val quad = Quadrilateral(tl, tr, br, bl)
        assertTrue(quad.signedArea > 0)
        assertEquals(quad.area, quad.signedArea, 1e-9)
        assertEquals(-quad.area, Quadrilateral(tl, bl, br, tr).signedArea, 1e-9)
    }

    @Test
    fun `side lengths are top right bottom left`() {
        val rect = Quadrilateral(PixelPoint(0.0, 0.0), PixelPoint(630.0, 0.0), PixelPoint(630.0, 880.0), PixelPoint(0.0, 880.0))
        assertEquals(listOf(630.0, 880.0, 630.0, 880.0), rect.sideLengths)
    }

    @Test
    fun `interior angles of rectangle trapezoid and concave outline`() {
        val rect = Quadrilateral(PixelPoint(0.0, 0.0), PixelPoint(630.0, 0.0), PixelPoint(630.0, 880.0), PixelPoint(0.0, 880.0))
        rect.interiorAnglesDeg.forEach { assertEquals(90.0, it, 1e-9) }

        // Isosceles trapezoid: wider at the bottom, so the top angles are obtuse.
        val trapezoid = Quadrilateral(PixelPoint(100.0, 0.0), PixelPoint(300.0, 0.0), PixelPoint(400.0, 100.0), PixelPoint(0.0, 100.0))
        val angles = trapezoid.interiorAnglesDeg
        assertEquals(135.0, angles[0], 1e-9)
        assertEquals(135.0, angles[1], 1e-9)
        assertEquals(45.0, angles[2], 1e-9)
        assertEquals(45.0, angles[3], 1e-9)

        val concave = Quadrilateral(PixelPoint(0.0, 0.0), PixelPoint(1000.0, 0.0), PixelPoint(400.0, 400.0), PixelPoint(0.0, 1000.0))
        assertTrue(concave.interiorAnglesDeg[2] > 180.0)
        assertEquals(360.0, concave.interiorAnglesDeg.sum(), 1e-9)
    }

    @Test
    fun `zero length side gives undefined angles`() {
        val collapsed = Quadrilateral(tl, tl, br, bl)
        assertTrue(collapsed.interiorAnglesDeg[0].isNaN())
        assertTrue(collapsed.interiorAnglesDeg[1].isNaN())
    }

    @Test
    fun `bow tie is self intersecting and a proper outline is not`() {
        // Top (TL–TR) crosses bottom (BR–BL).
        val bowTie = Quadrilateral(PixelPoint(0.0, 0.0), PixelPoint(100.0, 100.0), PixelPoint(100.0, 0.0), PixelPoint(0.0, 100.0))
        assertTrue(bowTie.isSelfIntersecting)
        assertFalse(Quadrilateral(tl, tr, br, bl).isSelfIntersecting)
    }

    @Test
    fun `bow tie crossing the other pair of opposite sides is self intersecting`() {
        // Right (TR–BR) crosses left (BL–TL); top and bottom do not cross.
        val bowTie = Quadrilateral(PixelPoint(0.0, 0.0), PixelPoint(100.0, 0.0), PixelPoint(0.0, 100.0), PixelPoint(100.0, 100.0))
        assertTrue(bowTie.isSelfIntersecting)
        assertFalse(bowTie.isConvex)
    }

    @Test
    fun `collinear corners are never convex in either winding`() {
        val rect = Quadrilateral(PixelPoint(0.0, 0.0), PixelPoint(630.0, 0.0), PixelPoint(630.0, 880.0), PixelPoint(0.0, 880.0))
        assertTrue(rect.isConvex)
        assertTrue("mirrored labels are still convex", Quadrilateral(rect.topLeft, rect.bottomLeft, rect.bottomRight, rect.topRight).isConvex)

        val allCollinear = Quadrilateral(PixelPoint(0.0, 0.0), PixelPoint(1.0, 0.0), PixelPoint(2.0, 0.0), PixelPoint(3.0, 0.0))
        assertFalse(allCollinear.isConvex)

        // TR lies on the straight line TL–BR: a 180° vertex, i.e. a triangle.
        val straightAngle = Quadrilateral(PixelPoint(0.0, 0.0), PixelPoint(5.0, 0.0), PixelPoint(10.0, 0.0), PixelPoint(0.0, 10.0))
        assertFalse(straightAngle.isConvex)
        val mirroredStraightAngle = Quadrilateral(straightAngle.topLeft, straightAngle.bottomLeft, straightAngle.bottomRight, straightAngle.topRight)
        assertTrue(mirroredStraightAngle.signedArea < 0)
        assertFalse(mirroredStraightAngle.isConvex)
    }

    @Test
    fun `requires exactly four points`() {
        assertThrows(IllegalArgumentException::class.java) { Quadrilateral.fromUnordered(listOf(tl, tr, br)) }
    }
}
