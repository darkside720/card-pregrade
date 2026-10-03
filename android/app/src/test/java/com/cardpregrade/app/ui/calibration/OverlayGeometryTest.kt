package com.cardpregrade.app.ui.calibration

import com.cardpregrade.core.cv.geometry.Line
import com.cardpregrade.core.cv.geometry.PixelPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Expected positions are worked by hand from Compose's Fit + Center placement: scale =
 * min(Vw/W, Vh/H) and a whole-pixel offset round((round(Vw) − round(W·s)) / 2), halves up.
 */
class OverlayGeometryTest {

    private fun assertView(m: FitMapping, image: Pair<Double, Double>, view: Pair<Double, Double>) {
        assertEquals("x of $image", view.first, m.toViewX(image.first), 1e-9)
        assertEquals("y of $image", view.second, m.toViewY(image.second), 1e-9)
    }

    @Test
    fun `same aspect ratio fills the view with no offset`() {
        // 771 × 1024 into 385.5 × 512: scale = min(0.5, 0.5) = 0.5; round(385.5) − round(385.5) = 0.
        val m = FitMapping.of(771, 1024, 385.5f, 512f)
        assertEquals(FitMapping(0.5, 0.0, 0.0), m)
        assertView(m, 0.0 to 0.0, 0.0 to 0.0)
        assertView(m, 771.0 to 1024.0, 385.5 to 512.0)
        assertView(m, 385.5 to 512.0, 192.75 to 256.0)
    }

    @Test
    fun `wider view letterboxes left and right`() {
        // 100 × 200 into 300 × 200: scale = min(3, 1) = 1, shown 100 wide, offsetX = (300 − 100) / 2 = 100.
        val m = FitMapping.of(100, 200, 300f, 200f)
        assertEquals(FitMapping(1.0, 100.0, 0.0), m)
        assertView(m, 0.0 to 0.0, 100.0 to 0.0)
        assertView(m, 100.0 to 0.0, 200.0 to 0.0)
        assertView(m, 100.0 to 200.0, 200.0 to 200.0)
        assertView(m, 0.0 to 200.0, 100.0 to 200.0)
        assertView(m, 50.0 to 100.0, 150.0 to 100.0)
    }

    @Test
    fun `taller view letterboxes top and bottom`() {
        // 200 × 100 into 200 × 300: scale 1, offsetY = (300 − 100) / 2 = 100.
        val m = FitMapping.of(200, 100, 200f, 300f)
        assertEquals(FitMapping(1.0, 0.0, 100.0), m)
        assertView(m, 0.0 to 0.0, 0.0 to 100.0)
        assertView(m, 200.0 to 100.0, 200.0 to 200.0)
        assertView(m, 100.0 to 50.0, 100.0 to 150.0)
    }

    @Test
    fun `scaled letterboxing uses a whole-pixel offset like Compose`() {
        // 1024 × 771 into 400 × 400: scale = min(0.390625, 0.5188…) = 0.390625, drawn height
        // 771 · 0.390625 = 301.171875 (unrounded), offsetY = round((400 − round(301.17)) / 2)
        // = round(99 / 2) = round(49.5) = 50, not the exact 49.414.
        val m = FitMapping.of(1024, 771, 400f, 400f)
        assertEquals(0.390625, m.scale, 0.0)
        assertEquals(0.0, m.offsetX, 0.0)
        assertEquals(50.0, m.offsetY, 0.0)
        assertView(m, 0.0 to 0.0, 0.0 to 50.0)
        assertView(m, 1024.0 to 771.0, 400.0 to 351.171875)
        assertView(m, 512.0 to 385.5, 200.0 to 200.5859375)
    }

    @Test
    fun `odd leftover pixels round the offset half up`() {
        // 100 × 200 into 301 × 200: scale 1, leftover 201 → 100.5 → 101.
        val h = FitMapping.of(100, 200, 301f, 200f)
        assertEquals(FitMapping(1.0, 101.0, 0.0), h)
        assertView(h, 100.0 to 200.0, 201.0 to 200.0)
        // 200 × 100 into 200 × 301: leftover 201 vertically → 101.
        assertEquals(FitMapping(1.0, 0.0, 101.0), FitMapping.of(200, 100, 200f, 301f))
        // 100 × 200 into 299 × 200: leftover 199 → 99.5 → 100.
        assertEquals(FitMapping(1.0, 100.0, 0.0), FitMapping.of(100, 200, 299f, 200f))
    }

    @Test
    fun `fractional scaled size is rounded before centring`() {
        // 300 × 200 into 200 × 200: scale = 200/300 in Float = 0.6666667, drawn height
        // 200 · 0.6666667 = 133.33334 → round 133, leftover 67 → 33.5 → 34 (exact centring: 33.33).
        val m = FitMapping.of(300, 200, 200f, 200f)
        assertEquals((200f / 300f).toDouble(), m.scale, 0.0)
        assertEquals(0.0, m.offsetX, 0.0)
        assertEquals(34.0, m.offsetY, 0.0)
        // Float scale 0.6666666865348816: (150, 100) → (150·s, 34 + 100·s).
        assertView(m, 150.0 to 100.0, 100.00000298023224 to 100.66666865348816)
    }

    @Test
    fun `aspect-ratio container leaves at most a sub-pixel letterbox and no offset`() {
        // aspectRatio(771/1024) on a 1080 px wide box gives height round(1080 · 1024 / 771) = 1434:
        // scale = min(1.40078, 1.40039) = 1.40039, drawn width 771 · 1.40039 = 1079.70 → 1080,
        // leftover 0 → offset 0 on both axes.
        val m = FitMapping.of(771, 1024, 1080f, 1434f)
        assertEquals(0.0, m.offsetX, 0.0)
        assertEquals(0.0, m.offsetY, 0.0)
        assertEquals((1434f / 1024f).toDouble(), m.scale, 0.0)
    }

    @Test
    fun `empty view maps everything to its origin and invalid images are rejected`() {
        assertEquals(FitMapping(0.0, 0.0, 0.0), FitMapping.of(100, 100, 0f, 0f))
        assertThrows(IllegalArgumentException::class.java) { FitMapping.of(0, 100, 10f, 10f) }
        assertThrows(IllegalArgumentException::class.java) { FitMapping.of(100, 100, -1f, 10f) }
    }

    // ---- clipping to [0, 200] × [0, 100] ----

    private val w = 200.0
    private val h = 100.0

    private fun clip(a: Double, b: Double, c: Double) = clipLineToRect(Line.of(a, b, c)!!, w, h)

    private fun assertSegment(expected: Set<Pair<Double, Double>>, actual: Pair<PixelPoint, PixelPoint>?, tol: Double = 1e-9) {
        if (actual == null) fail("Expected $expected, got no segment") else {
            val got = listOf(actual.first, actual.second)
            for ((x, y) in expected) {
                assertTrue("($x, $y) not an end of $actual", got.any { kotlin.math.abs(it.x - x) <= tol && kotlin.math.abs(it.y - y) <= tol })
            }
            assertTrue("ends must differ: $actual", actual.first.distanceTo(actual.second) > 0.0)
        }
    }

    @Test
    fun `horizontal and vertical lines span the rectangle`() {
        assertSegment(setOf(0.0 to 30.0, 200.0 to 30.0), clip(0.0, 1.0, -30.0)) // y = 30
        assertSegment(setOf(50.0 to 0.0, 50.0 to 100.0), clip(1.0, 0.0, -50.0)) // x = 50
    }

    @Test
    fun `lines on the boundary are kept as the boundary edge`() {
        assertSegment(setOf(0.0 to 0.0, 200.0 to 0.0), clip(0.0, 1.0, 0.0)) // y = 0
        assertSegment(setOf(200.0 to 0.0, 200.0 to 100.0), clip(1.0, 0.0, -200.0)) // x = 200
    }

    @Test
    fun `diagonals end on the edges they cross`() {
        // x + y = 150: y = 100 at x = 50, y = 0 at x = 150.
        assertSegment(setOf(50.0 to 100.0, 150.0 to 0.0), clip(1.0, 1.0, -150.0))
        // y = x: enters at the corner (0, 0), leaves through the bottom edge at (100, 100).
        assertSegment(setOf(0.0 to 0.0, 100.0 to 100.0), clip(1.0, -1.0, 0.0))
    }

    @Test
    fun `a line through two opposite corners yields exactly those corners once each`() {
        // x − 2y = 0 through (0, 0) and (200, 100); x + 2y = 200 through (0, 100) and (200, 0).
        assertSegment(setOf(0.0 to 0.0, 200.0 to 100.0), clip(1.0, -2.0, 0.0))
        assertSegment(setOf(0.0 to 100.0, 200.0 to 0.0), clip(1.0, 2.0, -200.0))
    }

    @Test
    fun `near-horizontal and near-vertical lines clip without large intermediate points`() {
        // 1e-9·x + y = 30: y = 30 at x = 0 and 30 − 2e-7 at x = 200.
        assertSegment(setOf(0.0 to 30.0, 200.0 to 30.0 - 2e-7), clip(1e-9, 1.0, -30.0), 1e-9)
        // x + 1e-9·y = 50: x = 50 at y = 0 and 50 − 1e-7 at y = 100.
        assertSegment(setOf(50.0 to 0.0, 50.0 - 1e-7 to 100.0), clip(1.0, 1e-9, -50.0), 1e-9)
    }

    @Test
    fun `lines that miss or only touch a corner give no segment`() {
        assertNull(clip(0.0, 1.0, 10.0)) // y = −10
        assertNull(clip(1.0, 0.0, -250.0)) // x = 250
        assertNull(clip(1.0, 1.0, -400.0)) // x + y = 400
        assertNull(clip(1.0, 1.0, 0.0)) // x + y = 0 touches only (0, 0)
        assertNull(clip(1.0, 1.0, -300.0)) // touches only (200, 100)
    }

    @Test
    fun `segment ends always lie inside the rectangle`() {
        for ((a, b, c) in listOf(Triple(0.3, 0.7, -40.0), Triple(-2.0, 1.0, 120.0), Triple(5.0, 0.01, -333.0), Triple(1.0, 1.0, -150.0))) {
            val s = clipLineToRect(Line.of(a, b, c)!!, w, h) ?: continue
            for (p in listOf(s.first, s.second)) assertTrue("$p", p.x in 0.0..w && p.y in 0.0..h)
        }
    }
}
