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
    fun `requires exactly four points`() {
        assertThrows(IllegalArgumentException::class.java) { Quadrilateral.fromUnordered(listOf(tl, tr, br)) }
    }
}
