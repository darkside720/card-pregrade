package com.cardpregrade.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CenteringRatioTest {

    @Test
    fun `equal borders produce 50-50`() {
        val ratio = CenteringRatio.fromBorders(30.0, 30.0)
        assertEquals(50.0, ratio.first, 1e-9)
        assertEquals(50.0, ratio.second, 1e-9)
        assertEquals("50.0 / 50.0", ratio.format())
    }

    @Test
    fun `unequal borders produce proportional split`() {
        val ratio = CenteringRatio.fromBorders(25.6, 24.4)
        assertEquals("51.2 / 48.8", ratio.format())
        assertEquals(51.2, ratio.larger, 1e-9)
    }

    @Test
    fun `split is independent of units`() {
        val px = CenteringRatio.fromBorders(60.0, 40.0)
        val mm = CenteringRatio.fromBorders(3.0, 2.0)
        assertEquals(px.first, mm.first, 1e-9)
    }

    @Test
    fun `zero total width is rejected rather than fabricated`() {
        assertThrows(IllegalArgumentException::class.java) { CenteringRatio.fromBorders(0.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { CenteringRatio.fromBorders(-1.0, 5.0) }
    }

    @Test
    fun `measured centering derives both axes from raw borders`() {
        val measured = CenteringResult.Measured(
            side = CardSide.BACK,
            borders = BorderWidths(left = 25.4, right = 24.6, top = 26.05, bottom = 23.95),
            confidence = Confidence(0.9),
            sourceImageId = "img",
            algorithmVersion = AlgorithmVersion("test", "0"),
        )
        assertEquals("50.8 / 49.2", measured.leftRight.format())
        assertEquals("52.1 / 47.9", measured.topBottom.format())
        assertFalse(measured.isLowConfidence)
    }

    @Test
    fun `low confidence measurement is flagged`() {
        val measured = CenteringResult.Measured(
            CardSide.FRONT, BorderWidths(1.0, 1.0, 1.0, 1.0), Confidence(0.3), "img", AlgorithmVersion("t", "0"),
        )
        assertTrue(measured.isLowConfidence)
    }
}
