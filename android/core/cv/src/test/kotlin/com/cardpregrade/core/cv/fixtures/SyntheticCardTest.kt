package com.cardpregrade.core.cv.fixtures

import com.cardpregrade.core.cv.geometry.PixelRect
import com.cardpregrade.core.model.CenteringRatio
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the synthetic fixture generator itself, so later analyzer tests can trust it as
 * ground truth.
 */
class SyntheticCardTest {

    // 630x880 card (10 px/mm) with deliberately off-centre art: L/R 36/27, T/B 40/40.
    private val spec = SyntheticCardSpec(
        canvasWidth = 800,
        canvasHeight = 1000,
        cardRect = PixelRect(85, 60, 715, 940),
        artRect = PixelRect(121, 100, 688, 900),
    )

    @Test
    fun `renders background card border and art at exact coordinates`() {
        val raster = SyntheticCard.render(spec)
        assertEquals(spec.backgroundArgb, raster[84, 500])
        assertEquals(spec.borderArgb, raster[85, 500])
        assertEquals(spec.borderArgb, raster[120, 500])
        assertEquals(spec.artArgb, raster[121, 500])
        assertEquals(spec.artArgb, raster[687, 500])
        assertEquals(spec.borderArgb, raster[688, 500])
        assertEquals(spec.backgroundArgb, raster[715, 500])
    }

    @Test
    fun `spec exposes ground truth borders and centering`() {
        assertEquals(36, spec.leftBorder)
        assertEquals(27, spec.rightBorder)
        val lr = CenteringRatio.fromBorders(spec.leftBorder.toDouble(), spec.rightBorder.toDouble())
        assertEquals("57.1 / 42.9", lr.format())
        val tb = CenteringRatio.fromBorders(spec.topBorder.toDouble(), spec.bottomBorder.toDouble())
        assertEquals("50.0 / 50.0", tb.format())
    }
}
