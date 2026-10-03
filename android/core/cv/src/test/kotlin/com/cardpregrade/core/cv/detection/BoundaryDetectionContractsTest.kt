package com.cardpregrade.core.cv.detection

import com.cardpregrade.core.cv.geometry.Line
import com.cardpregrade.core.cv.geometry.PixelPoint
import com.cardpregrade.core.cv.geometry.QuadValidationPolicy
import com.cardpregrade.core.cv.geometry.Quadrilateral
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BoundaryDetectionContractsTest {

    @Test
    fun `sides know which scans observe them and which way is inward`() {
        assertTrue(Side.LEFT.scannedByRows && Side.RIGHT.scannedByRows)
        assertFalse(Side.TOP.scannedByRows || Side.BOTTOM.scannedByRows)
        assertEquals(1, Side.LEFT.inwardSign)
        assertEquals(1, Side.TOP.inwardSign)
        assertEquals(-1, Side.RIGHT.inwardSign)
        assertEquals(-1, Side.BOTTOM.inwardSign)
    }

    @Test
    fun `polarity follows the response sign relative to the inward direction`() {
        // Bright card on a dark background: rowDx rises at the left edge (+) and falls at the right (−);
        // both are "inward brighter". Same for columnDy at top (+) and bottom (−).
        assertEquals(EdgePolarity.INWARD_BRIGHTER, EdgePolarity.of(Side.LEFT, 684))
        assertEquals(EdgePolarity.INWARD_BRIGHTER, EdgePolarity.of(Side.RIGHT, -684))
        assertEquals(EdgePolarity.INWARD_BRIGHTER, EdgePolarity.of(Side.TOP, 1))
        assertEquals(EdgePolarity.INWARD_BRIGHTER, EdgePolarity.of(Side.BOTTOM, -1))
        // Dark card on a bright background reverses every side.
        assertEquals(EdgePolarity.INWARD_DARKER, EdgePolarity.of(Side.LEFT, -208))
        assertEquals(EdgePolarity.INWARD_DARKER, EdgePolarity.of(Side.RIGHT, 208))
        assertEquals(EdgePolarity.INWARD_DARKER, EdgePolarity.of(Side.TOP, -1))
        assertEquals(EdgePolarity.INWARD_DARKER, EdgePolarity.of(Side.BOTTOM, Int.MAX_VALUE))
        assertEquals(EdgePolarity.INWARD_DARKER, EdgePolarity.of(Side.LEFT, Int.MIN_VALUE))
        assertThrows(IllegalArgumentException::class.java) { EdgePolarity.of(Side.LEFT, 0) }
    }

    @Test
    fun `observation polarity is derived from its side and response`() {
        val o = SideObservation(Side.RIGHT, scanIndex = 3, scanCoordinate = 120, rank = 0, point = PixelPoint(400.0, 120.5), response = -300)
        assertEquals(EdgePolarity.INWARD_BRIGHTER, o.polarity)
    }

    @Test
    fun `observations reject a zero response or a point off their scan line`() {
        assertThrows(IllegalArgumentException::class.java) {
            SideObservation(Side.LEFT, 0, 120, 0, PixelPoint(10.0, 120.5), 0)
        }
        // Row observation missing the +0.5 pixel-centre offset.
        assertThrows(IllegalArgumentException::class.java) {
            SideObservation(Side.LEFT, 0, 120, 0, PixelPoint(10.0, 120.0), 50)
        }
        // Column observation with x and y swapped.
        assertThrows(IllegalArgumentException::class.java) {
            SideObservation(Side.TOP, 0, 30, 0, PixelPoint(12.0, 30.5), 50)
        }
        SideObservation(Side.TOP, 0, 30, 0, PixelPoint(30.5, 12.0), 50)
    }

    @Test
    fun `side fits and detections enforce their invariants`() {
        val line = Line.of(1.0, 0.0, -10.0)!!
        val bright = SideObservation(Side.LEFT, 0, 5, 0, PixelPoint(10.0, 5.5), 300)
        val dark = SideObservation(Side.LEFT, 1, 9, 0, PixelPoint(10.0, 9.5), -300)
        assertThrows(IllegalArgumentException::class.java) {
            SideFit(Side.LEFT, line, EdgePolarity.INWARD_BRIGHTER, listOf(bright, dark), 0.0, 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SideFit(Side.LEFT, line, EdgePolarity.INWARD_BRIGHTER, listOf(bright, bright.copy(rank = 1)), 0.0, 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SideFit(Side.RIGHT, line, EdgePolarity.INWARD_BRIGHTER, listOf(bright), 0.0, 0.0)
        }
        val left = SideFit(Side.LEFT, line, EdgePolarity.INWARD_BRIGHTER, listOf(bright), 0.0, 0.0)
        val diagnostics = DetectionDiagnostics(emptyList(), emptyList(), emptyMap())
        val quad = Quadrilateral(PixelPoint(0.0, 0.0), PixelPoint(1.0, 0.0), PixelPoint(1.0, 1.0), PixelPoint(0.0, 1.0))
        assertThrows(IllegalArgumentException::class.java) { BoundaryDetection.Detected(quad, mapOf(Side.LEFT to left), diagnostics) }
        assertThrows(IllegalArgumentException::class.java) {
            DetectionDiagnostics(emptyList(), emptyList(), mapOf(Side.TOP to SideDiagnostics(Side.LEFT, 0, 0, 0, null, null)))
        }
    }

    @Test
    fun `default policy is valid and gates only on structure`() {
        val p = CardDetectionPolicy.DEFAULT
        assertEquals(QuadValidationPolicy.STRUCTURAL_ONLY, p.validation)
    }

    @Test
    fun `policy rejects meaningless values`() {
        val p = CardDetectionPolicy.DEFAULT
        val invalid = listOf<Pair<String, () -> Unit>>(
            "one scan" to { p.copy(scanLinesPerAxis = 1) },
            "negative threshold" to { p.copy(minAbsResponse = -1) },
            "threshold above max Sobel" to { p.copy(minAbsResponse = 1021) },
            "no candidates" to { p.copy(candidatesPerScanEnd = 0) },
            "zero tolerance" to { p.copy(inlierTolerancePx = 0.0) },
            "negative tolerance" to { p.copy(inlierTolerancePx = -1.0) },
            "NaN tolerance" to { p.copy(inlierTolerancePx = Double.NaN) },
            "infinite tolerance" to { p.copy(inlierTolerancePx = Double.POSITIVE_INFINITY) },
            "one inlier" to { p.copy(minInliers = 1) },
            "more inliers than scans" to { p.copy(scanLinesPerAxis = 5, minInliers = 6) },
            "zero fraction" to { p.copy(supportFraction = 0.0) },
            "negative fraction" to { p.copy(supportFraction = -0.5) },
            "NaN fraction" to { p.copy(supportFraction = Double.NaN) },
            "fraction above 1" to { p.copy(supportFraction = 1.5) },
            "no refit" to { p.copy(maxRefitIterations = 0) },
        )
        for ((name, make) in invalid) {
            try {
                make()
                fail("$name was accepted")
            } catch (_: IllegalArgumentException) {
            }
        }
        // Boundary values are accepted.
        p.copy(scanLinesPerAxis = 2, minAbsResponse = 0, candidatesPerScanEnd = 1, minInliers = 2, supportFraction = 1.0, maxRefitIterations = 1)
        p.copy(minAbsResponse = 1020)
        p.copy(scanLinesPerAxis = 6, minInliers = 6)
    }
}
