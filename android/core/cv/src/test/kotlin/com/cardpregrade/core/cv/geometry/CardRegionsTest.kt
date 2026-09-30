package com.cardpregrade.core.cv.geometry

import com.cardpregrade.core.model.CardCorner
import com.cardpregrade.core.model.CardEdge
import com.cardpregrade.core.model.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CardRegionsTest {

    private val size = CardGeometry.correctedSize(630) // 630 x 880

    @Test
    fun `corrected size preserves physical aspect ratio`() {
        assertEquals(PixelSize(630, 880), size)
    }

    @Test
    fun `corner crops are physically square and anchored to their corner`() {
        val mapper = CoordinateMapper(size)
        for (corner in CardCorner.entries) {
            val px = mapper.toPixels(CardRegions.cornerRegion(corner, fraction = 0.1))
            assertEquals("width of $corner", 63, px.width)
            assertEquals("height of $corner", 63, px.height)
        }
        assertEquals(PixelRect(567, 817, 630, 880), mapper.toPixels(CardRegions.cornerRegion(CardCorner.BOTTOM_RIGHT, 0.1)))
        assertEquals(PixelRect(0, 0, 63, 63), mapper.toPixels(CardRegions.cornerRegion(CardCorner.TOP_LEFT, 0.1)))
    }

    @Test
    fun `edge bands exclude corner crops`() {
        val mapper = CoordinateMapper(size)
        val top = mapper.toPixels(CardRegions.edgeRegion(CardEdge.TOP, bandFraction = 0.05, cornerFraction = 0.1))
        assertEquals(PixelRect(63, 0, 567, 32), top)
        val right = mapper.toPixels(CardRegions.edgeRegion(CardEdge.RIGHT, bandFraction = 0.05, cornerFraction = 0.1))
        assertEquals(PixelRect(598, 63, 630, 817), right)

        val corner = CardRegions.cornerRegion(CardCorner.TOP_LEFT, 0.1)
        val topEdge = CardRegions.edgeRegion(CardEdge.TOP, 0.05, 0.1)
        assertFalse("edge band must not overlap the corner crop", overlaps(corner, topEdge))
    }

    @Test
    fun `pixel to normalized round trip`() {
        val mapper = CoordinateMapper(size)
        val rect = PixelRect(63, 88, 315, 440)
        assertEquals(rect, mapper.toPixels(mapper.toNormalized(rect)))
    }

    private fun overlaps(a: NormalizedRect, b: NormalizedRect): Boolean =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
}
