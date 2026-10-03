package com.cardpregrade.core.cv.fixtures

import com.cardpregrade.core.cv.geometry.CardGeometry
import com.cardpregrade.core.cv.geometry.PixelSize
import com.cardpregrade.core.model.CardCorner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Expected positions are literal pixel numbers, not values read back from the design. */
class CardPatternTest {

    private val k4 = StandardCardDesign(pxPerMm = 4)
    private val canonical4 = SyntheticSceneRenderer.renderCanonical(k4)
    private val art = setOf(StandardCardDesign.ART_LIGHT_ARGB, StandardCardDesign.ART_DARK_ARGB)
    private val border = StandardCardDesign.BORDER_ARGB

    @Test
    fun `canonical sizes are exactly 63k by 88k and agree with CardGeometry`() {
        assertEquals(PixelSize(252, 352), StandardCardDesign(4).canonicalSize)
        assertEquals(PixelSize(630, 880), StandardCardDesign(10).canonicalSize)
        assertEquals(PixelSize(1008, 1408), StandardCardDesign(16).canonicalSize)
        for (k in listOf(4, 10, 16)) assertEquals(CardGeometry.correctedSize(63 * k), StandardCardDesign(k).canonicalSize)
        assertThrows(IllegalArgumentException::class.java) { StandardCardDesign(0) }
    }

    @Test
    fun `margins are asymmetric on all four sides`() {
        val m = k4.margins
        assertEquals(MarginsMm(left = 3.5, right = 2.75, top = 4.0, bottom = 3.25), m)
        assertEquals(4, setOf(m.left, m.right, m.top, m.bottom).size)
        assertEquals(CardRegion(14.0, 16.0, 241.0, 339.0), k4.artBox)
        assertEquals(CardRegion(56.0, 64.0, 964.0, 1356.0), StandardCardDesign(16).artBox)
        assertEquals(CardRegion(35.0, 40.0, 602.5, 847.5), StandardCardDesign(10).artBox)
    }

    @Test
    fun `margin boundaries fall exactly on pixel edges at 4 px per mm`() {
        // Row 176 and column 126 avoid the corner markers.
        assertEquals(border, canonical4[13, 176])
        assertTrue(canonical4[14, 176] in art)
        assertTrue(canonical4[240, 176] in art)
        assertEquals(border, canonical4[241, 176])
        assertEquals(border, canonical4[126, 15])
        assertTrue(canonical4[126, 16] in art)
        assertTrue(canonical4[126, 338] in art)
        assertEquals(border, canonical4[126, 339])
    }

    @Test
    fun `margin boundaries fall exactly on pixel edges at 16 px per mm`() {
        val raster = SyntheticSceneRenderer.renderCanonical(StandardCardDesign(16))
        assertEquals(PixelSize(1008, 1408), PixelSize(raster.width, raster.height))
        assertEquals(border, raster[55, 704])
        assertTrue(raster[56, 704] in art)
        assertTrue(raster[963, 704] in art)
        assertEquals(border, raster[964, 704])
        assertEquals(border, raster[504, 63])
        assertTrue(raster[504, 64] in art)
        assertTrue(raster[504, 1355] in art)
        assertEquals(border, raster[504, 1356])
    }

    @Test
    fun `half pixel margins at 10 px per mm follow the half open rule`() {
        val k10 = StandardCardDesign(10)
        // Right art edge at u = 602.5: pixel 601 (centre 601.5) is art, pixel 602 (centre 602.5) is border.
        assertTrue(k10.argbAt(601.5, 440.5) in art)
        assertEquals(border, k10.argbAt(602.5, 440.5))
        // Bottom art edge at v = 847.5.
        assertTrue(k10.argbAt(315.5, 846.5) in art)
        assertEquals(border, k10.argbAt(315.5, 847.5))

        // The canonical raster samples pixel centres: integer-corner sampling would make column
        // 602 (u = 602.0 < 602.5) and row 847 art instead of border.
        val raster = SyntheticSceneRenderer.renderCanonical(k10)
        assertTrue(raster[601, 440] in art)
        assertEquals(border, raster[602, 440])
        assertTrue(raster[315, 846] in art)
        assertEquals(border, raster[315, 847])
    }

    @Test
    fun `orientation markers sit in the art box corners`() {
        assertEquals(CardRegion(14.0, 16.0, 38.0, 40.0), k4.markers[CardCorner.TOP_LEFT])
        assertEquals(CardRegion(217.0, 16.0, 241.0, 40.0), k4.markers[CardCorner.TOP_RIGHT])
        assertEquals(CardRegion(217.0, 315.0, 241.0, 339.0), k4.markers[CardCorner.BOTTOM_RIGHT])
        assertEquals(CardRegion(14.0, 315.0, 38.0, 339.0), k4.markers[CardCorner.BOTTOM_LEFT])

        val tl = StandardCardDesign.MARKER_ARGB.getValue(CardCorner.TOP_LEFT)
        assertEquals(tl, canonical4[14, 16])
        assertEquals(tl, canonical4[37, 39])
        assertTrue(canonical4[38, 39] in art)
        assertEquals(border, canonical4[13, 39])
        assertEquals(StandardCardDesign.MARKER_ARGB.getValue(CardCorner.TOP_RIGHT), canonical4[229, 28])
        assertEquals(StandardCardDesign.MARKER_ARGB.getValue(CardCorner.BOTTOM_RIGHT), canonical4[229, 327])
        assertEquals(StandardCardDesign.MARKER_ARGB.getValue(CardCorner.BOTTOM_LEFT), canonical4[26, 327])
    }

    @Test
    fun `marker colours are distinct in colour and in BT601 grey`() {
        val colours = CardCorner.entries.map { StandardCardDesign.MARKER_ARGB.getValue(it) }
        assertEquals(4, colours.toSet().size)
        val grey = ArgbRaster(4, 1, colours.toIntArray()).toGrayImage()
        val lumas = (0 until 4).map { grey[it, 0] }
        for (i in 0 until 4) for (j in i + 1 until 4) {
            assertTrue("luma ${lumas[i]} vs ${lumas[j]}", abs(lumas[i] - lumas[j]) >= 40)
        }
        assertTrue(colours.none { it == border || it in art })
    }

    @Test
    fun `art box is a 4 mm checkerboard anchored at its top left`() {
        // Cells are 16 px at 4 px/mm starting at (14, 16); cell (3, 5) is even, (4, 5) and (3, 6) are odd.
        assertEquals(StandardCardDesign.ART_LIGHT_ARGB, canonical4[70, 104])
        assertEquals(StandardCardDesign.ART_DARK_ARGB, canonical4[85, 104])
        assertEquals(StandardCardDesign.ART_DARK_ARGB, canonical4[70, 120])
        assertNotEquals(canonical4[77, 104], canonical4[78, 104])
    }

    @Test
    fun `pattern is defined only on the half open card and uses only its palette`() {
        assertThrows(IllegalArgumentException::class.java) { k4.argbAt(-0.1, 10.0) }
        assertThrows(IllegalArgumentException::class.java) { k4.argbAt(252.0, 10.0) }
        assertThrows(IllegalArgumentException::class.java) { k4.argbAt(10.0, 352.0) }
        assertTrue(canonical4.pixels.all { it in StandardCardDesign.PALETTE })
        assertTrue(StandardCardDesign.PALETTE.all { (it ushr 24) == 0xFF })
        assertTrue(SceneRequest.DEFAULT_BACKGROUND_ARGB !in StandardCardDesign.PALETTE)
    }
}
