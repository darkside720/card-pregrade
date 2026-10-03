package com.cardpregrade.core.cv.image

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Expected responses are derived by hand from the kernels
 *
 * ```
 * dx:  -1  0 +1        dy:  -1 -2 -1
 *      -2  0 +2              0  0  0
 *      -1  0 +1             +1 +2 +1
 * ```
 *
 * i.e. dx = (right column, weights 1 2 1) − (left column) and dy = (row below) − (row above).
 * Invalid input must fail with [IllegalArgumentException] specifically: an unchecked x = 0 or
 * x = width − 1 would silently read the neighbouring row instead of throwing.
 */
class SobelTest {

    private fun image(width: Int, height: Int, f: (x: Int, y: Int) -> Int) =
        GrayImage(width, height, IntArray(width * height) { f(it % width, it / width) })

    private fun forEachCentre(image: GrayImage, block: (x: Int, y: Int) -> Unit) {
        for (y in 1..image.height - 2) for (x in 1..image.width - 2) block(x, y)
    }

    private fun assertInvalid(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { block() }
    }

    @Test
    fun `constant image has zero gradient everywhere`() {
        val img = image(6, 5) { _, _ -> 137 }
        forEachCentre(img) { x, y ->
            assertEquals(0, Sobel.dx(img, x, y))
            assertEquals(0, Sobel.dy(img, x, y))
        }
    }

    @Test
    fun `horizontal ramp gives dx of 8 times slope and zero dy`() {
        // I = 10x. dx = (1+2+1)·10(x+1) − (1+2+1)·10(x−1) = 40·2 = 80.
        // Every row is identical, so row below − row above = 0.
        val img = image(7, 5) { x, _ -> 10 * x }
        forEachCentre(img) { x, y ->
            assertEquals(80, Sobel.dx(img, x, y))
            assertEquals(0, Sobel.dy(img, x, y))
        }
    }

    @Test
    fun `vertical ramp increasing downward gives positive dy and zero dx`() {
        // I = 10y, +y is down. dy = 4·10(y+1) − 4·10(y−1) = 80. Every column identical → dx = 0.
        val img = image(5, 7) { _, y -> 10 * y }
        forEachCentre(img) { x, y ->
            assertEquals(0, Sobel.dx(img, x, y))
            assertEquals(80, Sobel.dy(img, x, y))
        }
    }

    @Test
    fun `vertical ramp increasing upward gives negative dy`() {
        // I = 10·(6 − y): brighter towards the top, so dy = −80.
        val img = image(5, 7) { _, y -> 10 * (6 - y) }
        forEachCentre(img) { x, y -> assertEquals(-80, Sobel.dy(img, x, y)) }
    }

    @Test
    fun `positive step along y gives positive dy on the two rows straddling the edge`() {
        // 5×6, I = 0 for y < 3, 100 for y ≥ 3 (dark above, bright below).
        // Centre y=1: rows 0,2 both 0 → 0.     y=2: row 3 − row 1 = 4·100 − 0 = 400.
        // Centre y=3: row 4 − row 2 = 400.      y=4: rows 3,5 both 100 → 0.
        val img = image(5, 6) { _, y -> if (y >= 3) 100 else 0 }
        assertArrayEquals(intArrayOf(0, 400, 400, 0), Sobel.columnDy(img, 2, 1, 5))
        forEachCentre(img) { x, y -> assertEquals(0, Sobel.dx(img, x, y)) }
    }

    @Test
    fun `negative step along y gives negative dy`() {
        // Bright above, dark below: same magnitudes as the positive step, opposite sign.
        val img = image(5, 6) { _, y -> if (y >= 3) 0 else 100 }
        assertArrayEquals(intArrayOf(0, -400, -400, 0), Sobel.columnDy(img, 2, 1, 5))
        forEachCentre(img) { x, y -> assertEquals(0, Sobel.dx(img, x, y)) }
    }

    @Test
    fun `positive step along x gives positive dx on the two columns straddling the edge`() {
        // 6×5, I = 0 for x < 3, 100 for x ≥ 3 (dark left, bright right).
        // Centre x=1: 0.  x=2: col 3 − col 1 = 400.  x=3: col 4 − col 2 = 400.  x=4: 0.
        val img = image(6, 5) { x, _ -> if (x >= 3) 100 else 0 }
        assertArrayEquals(intArrayOf(0, 400, 400, 0), Sobel.rowDx(img, 2, 1, 5))
        forEachCentre(img) { x, y -> assertEquals(0, Sobel.dy(img, x, y)) }
    }

    @Test
    fun `negative step along x gives negative dx`() {
        val img = image(6, 5) { x, _ -> if (x >= 3) 0 else 100 }
        assertArrayEquals(intArrayOf(0, -400, -400, 0), Sobel.rowDx(img, 2, 1, 5))
        forEachCentre(img) { x, y -> assertEquals(0, Sobel.dy(img, x, y)) }
    }

    @Test
    fun `full-scale step reaches the documented extreme response`() {
        // 0 → 255 step: 4·255 = 1020, the documented bound for 8-bit luma.
        val right = image(4, 3) { x, _ -> if (x >= 2) 255 else 0 }
        assertEquals(1020, Sobel.dx(right, 1, 1))
        assertEquals(-1020, Sobel.dx(image(4, 3) { x, _ -> if (x >= 2) 0 else 255 }, 1, 1))
        assertEquals(1020, Sobel.dy(image(3, 4) { _, y -> if (y >= 2) 255 else 0 }, 1, 1))
    }

    @Test
    fun `diagonal step above the main diagonal gives positive dx and negative dy`() {
        // 5×5, I = 100 where x > y, else 0. Neighbourhood of centre (2,2), rows y = 1..3:
        //   y=1:   0 100 100
        //   y=2:   0   0 100
        //   y=3:   0   0   0
        // dx = (100 + 2·100 + 0) − (0 + 0 + 0) = 300
        // dy = (0 + 0 + 0) − (0 + 2·100 + 100) = −300
        val img = image(5, 5) { x, y -> if (x > y) 100 else 0 }
        assertEquals(300, Sobel.dx(img, 2, 2))
        assertEquals(-300, Sobel.dy(img, 2, 2))
    }

    @Test
    fun `asymmetric neighbourhood distinguishes dx from dy and every kernel weight`() {
        //   1  2  3
        //   4  5  7
        //   9 11 20
        // dx = (3 + 2·7 + 20) − (1 + 2·4 + 9)  = 37 − 18 = 19
        // dy = (9 + 2·11 + 20) − (1 + 2·2 + 3) = 51 −  8 = 43
        val img = GrayImage(3, 3, intArrayOf(1, 2, 3, 4, 5, 7, 9, 11, 20))
        assertEquals(19, Sobel.dx(img, 1, 1))
        assertEquals(43, Sobel.dy(img, 1, 1))
    }

    @Test
    fun `rowDx extracts exactly the half-open range in order`() {
        // 7×3, I = x². dx = 4·((x+1)² − (x−1)²) = 16x, so every centre has a distinct value.
        // Valid centres x = 1..5, so the largest legal untilX is 6.
        val img = image(7, 3) { x, _ -> x * x }
        assertArrayEquals(intArrayOf(16, 32, 48, 64, 80), Sobel.rowDx(img, 1, 1, 6))
        assertArrayEquals(intArrayOf(32, 48, 64), Sobel.rowDx(img, 1, 2, 5))
        assertArrayEquals(intArrayOf(80), Sobel.rowDx(img, 1, 5, 6))
        for (x in 1..5) assertEquals(Sobel.dx(img, x, 1), Sobel.rowDx(img, 1, x, x + 1).single())
    }

    @Test
    fun `columnDy extracts exactly the half-open range in order`() {
        // 3×7, I = y². dy = 16y. Valid centres y = 1..5, largest legal untilY is 6.
        val img = image(3, 7) { _, y -> y * y }
        assertArrayEquals(intArrayOf(16, 32, 48, 64, 80), Sobel.columnDy(img, 1, 1, 6))
        assertArrayEquals(intArrayOf(32, 48, 64), Sobel.columnDy(img, 1, 2, 5))
        assertArrayEquals(intArrayOf(80), Sobel.columnDy(img, 1, 5, 6))
        for (y in 1..5) assertEquals(Sobel.dy(img, 1, y), Sobel.columnDy(img, 1, y, y + 1).single())
    }

    @Test
    fun `empty profile ranges return empty arrays anywhere inside the legal bounds`() {
        val img = image(7, 7) { x, y -> x + y }
        assertEquals(0, Sobel.rowDx(img, 3, 1, 1).size)
        assertEquals(0, Sobel.rowDx(img, 3, 4, 4).size)
        assertEquals(0, Sobel.rowDx(img, 3, 6, 6).size)
        assertEquals(0, Sobel.columnDy(img, 3, 1, 1).size)
        assertEquals(0, Sobel.columnDy(img, 3, 4, 4).size)
        assertEquals(0, Sobel.columnDy(img, 3, 6, 6).size)
        // An empty range does not excuse an out-of-bounds start or an invalid row/column.
        assertInvalid { Sobel.rowDx(img, 3, 0, 0) }
        assertInvalid { Sobel.rowDx(img, 3, 7, 7) }
        assertInvalid { Sobel.rowDx(img, 0, 3, 3) }
        assertInvalid { Sobel.columnDy(img, 3, 0, 0) }
        assertInvalid { Sobel.columnDy(img, 3, 7, 7) }
        assertInvalid { Sobel.columnDy(img, 6, 3, 3) }
    }

    @Test
    fun `boundary centres are valid and use the correct neighbours`() {
        // 6×5, I = 10x + 3y: dx = 80 and dy = 24 everywhere, including the outermost valid centres.
        val img = image(6, 5) { x, y -> 10 * x + 3 * y }
        for ((x, y) in listOf(1 to 1, 4 to 1, 1 to 3, 4 to 3)) {
            assertEquals(80, Sobel.dx(img, x, y))
            assertEquals(24, Sobel.dy(img, x, y))
        }
    }

    @Test
    fun `centres on or beyond the image border are rejected`() {
        // 6×5: valid x = 1..4, y = 1..3. I = x² · (y+1) makes wrapped reads give wrong-but-plausible
        // numbers, so only an explicit check can make these throw IllegalArgumentException.
        val img = image(6, 5) { x, y -> x * x * (y + 1) }
        val invalid = listOf(0 to 2, 5 to 2, 2 to 0, 2 to 4, -1 to 2, 6 to 2, 2 to -1, 2 to 5, 0 to 0, 5 to 4)
        for ((x, y) in invalid) {
            assertInvalid { Sobel.dx(img, x, y) }
            assertInvalid { Sobel.dy(img, x, y) }
        }
    }

    @Test
    fun `profiles reject rows or columns without valid centres`() {
        val img = image(6, 5) { x, y -> x + y }
        for (y in listOf(-1, 0, 4, 5)) assertInvalid { Sobel.rowDx(img, y, 1, 5) }
        for (x in listOf(-1, 0, 5, 6)) assertInvalid { Sobel.columnDy(img, x, 1, 4) }
    }

    @Test
    fun `profile ranges reaching outside the valid centres are rejected not truncated`() {
        // 6×5: rowDx needs 1 ≤ fromX ≤ untilX ≤ 5; columnDy needs 1 ≤ fromY ≤ untilY ≤ 4.
        val img = image(6, 5) { x, y -> x + y }
        assertInvalid { Sobel.rowDx(img, 2, 0, 5) }   // starts on the border column
        assertInvalid { Sobel.rowDx(img, 2, 1, 6) }   // would include x = 5
        assertInvalid { Sobel.rowDx(img, 2, -3, 2) }
        assertInvalid { Sobel.rowDx(img, 2, 1, 100) }
        assertInvalid { Sobel.rowDx(img, 2, 3, 2) }   // reversed
        assertInvalid { Sobel.columnDy(img, 2, 0, 4) }
        assertInvalid { Sobel.columnDy(img, 2, 1, 5) }  // would include y = 4
        assertInvalid { Sobel.columnDy(img, 2, -3, 2) }
        assertInvalid { Sobel.columnDy(img, 2, 1, 100) }
        assertInvalid { Sobel.columnDy(img, 2, 3, 2) }
    }

    @Test
    fun `three by three image has exactly one valid centre`() {
        val img = GrayImage(3, 3, intArrayOf(1, 2, 3, 4, 5, 7, 9, 11, 20))
        assertArrayEquals(intArrayOf(19), Sobel.rowDx(img, 1, 1, 2))
        assertArrayEquals(intArrayOf(43), Sobel.columnDy(img, 1, 1, 2))
        assertInvalid { Sobel.rowDx(img, 1, 1, 3) }
        assertInvalid { Sobel.columnDy(img, 1, 1, 3) }
        for ((x, y) in listOf(0 to 1, 2 to 1, 1 to 0, 1 to 2)) assertInvalid { Sobel.dx(img, x, y) }
    }

    @Test
    fun `images narrower or shorter than three pixels have no valid centre`() {
        for ((w, h) in listOf(1 to 1, 2 to 2, 2 to 5, 5 to 2, 1 to 5, 5 to 1)) {
            val img = image(w, h) { x, y -> x + y }
            for (y in -1..h) for (x in -1..w) {
                assertInvalid { Sobel.dx(img, x, y) }
                assertInvalid { Sobel.dy(img, x, y) }
            }
            if (h < 3) for (y in -1..h) assertInvalid { Sobel.rowDx(img, y, 1, 1) }
            if (w < 3) for (x in -1..w) assertInvalid { Sobel.columnDy(img, x, 1, 1) }
        }
    }

    @Test
    fun `degenerate widths and heights follow the documented profile bounds exactly`() {
        // rowDx needs 1 ≤ fromX ≤ untilX ≤ width − 1. Width 2 admits only the empty range [1, 1);
        // width 1 admits none. Symmetrically for columnDy and height.
        val narrow = image(2, 5) { x, y -> x + y }
        assertEquals(0, Sobel.rowDx(narrow, 2, 1, 1).size)
        assertInvalid { Sobel.rowDx(narrow, 2, 1, 2) }
        assertInvalid { Sobel.rowDx(image(1, 5) { _, y -> y }, 2, 1, 1) }

        val short = image(5, 2) { x, y -> x + y }
        assertEquals(0, Sobel.columnDy(short, 2, 1, 1).size)
        assertInvalid { Sobel.columnDy(short, 2, 1, 2) }
        assertInvalid { Sobel.columnDy(image(5, 1) { x, _ -> x }, 2, 1, 1) }
    }
}
