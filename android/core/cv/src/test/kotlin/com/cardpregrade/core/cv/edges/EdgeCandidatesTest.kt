package com.cardpregrade.core.cv.edges

import com.cardpregrade.core.cv.image.GrayImage
import com.cardpregrade.core.cv.image.Sobel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.round
import kotlin.random.Random

/**
 * Expected positions are worked out by hand from the documented rules:
 * - profile[i] sits at firstPixel + i + 0.5;
 * - a plateau [lo, hi] is reported at firstPixel + (lo + hi) / 2 + 0.5;
 * - a single-sample peak at i is reported at firstPixel + i + 0.5 + δ with
 *   δ = (a − c) / (2 (a − 2b + c)), where a, b, c are the polarity-projected neighbours/peak.
 */
class EdgeCandidatesTest {

    private fun find(vararg profile: Int, firstPixel: Int = 0, min: Int = 1) =
        EdgeCandidates.find(profile, firstPixel, min)

    private fun assertCandidates(expected: List<Pair<Double, Int>>, actual: List<EdgeCandidate>) {
        assertEquals(actual.toString(), expected.size, actual.size)
        for ((e, a) in expected.zip(actual)) {
            assertEquals(actual.toString(), e.first, a.position, 1e-12)
            assertEquals(actual.toString(), e.second, a.response)
        }
    }

    @Test
    fun `empty and too-short profiles yield nothing`() {
        assertEquals(emptyList<EdgeCandidate>(), find())
        assertEquals(emptyList<EdgeCandidate>(), find(100))
        assertEquals(emptyList<EdgeCandidate>(), find(0, 100))
        assertEquals(emptyList<EdgeCandidate>(), find(100, 0))
        assertEquals(emptyList<EdgeCandidate>(), find(-100, 100))
    }

    @Test
    fun `three samples are enough for a candidate`() {
        // i = 1, a = c = 0 → δ = 0 → 0 + 1 + 0.5.
        assertCandidates(listOf(1.5 to 100), find(0, 100, 0))
    }

    @Test
    fun `zero is never a candidate even with a zero threshold`() {
        assertEquals(emptyList<EdgeCandidate>(), find(0, 0, 0, 0, 0, min = 0))
        assertEquals(emptyList<EdgeCandidate>(), find(5, 0, 5, min = 0))
    }

    @Test
    fun `zero threshold accepts any non-zero extremum`() {
        assertCandidates(listOf(1.5 to 1, 3.5 to -1), find(0, 1, 0, -1, 0, min = 0))
    }

    @Test
    fun `threshold is inclusive on absolute response for both polarities`() {
        assertCandidates(listOf(1.5 to 50), find(0, 50, 0, min = 50))
        assertCandidates(listOf(1.5 to -50), find(0, -50, 0, min = 50))
        assertEquals(emptyList<EdgeCandidate>(), find(0, 50, 0, min = 51))
        assertEquals(emptyList<EdgeCandidate>(), find(0, -50, 0, min = 51))
        assertEquals(emptyList<EdgeCandidate>(), find(0, 10, 30, 20, 0, min = 31))
    }

    @Test
    fun `positive isolated symmetric peak sits on its sample centre`() {
        // i = 2, a = c = 10 → δ = 0 → 2.5.
        assertCandidates(listOf(2.5 to 40), find(0, 10, 40, 10, 0))
    }

    @Test
    fun `negative isolated symmetric peak keeps its sign`() {
        // s = −1: a = c = 10, b = 40 → δ = 0 → 2.5.
        assertCandidates(listOf(2.5 to -40), find(0, -10, -40, -10, 0))
    }

    @Test
    fun `multiple peaks are reported in order and weak ones are dropped`() {
        // +100 at i = 1 → 1.5; −200 at i = 4 → 4.5; +50 at i = 6 is below 60.
        assertCandidates(listOf(1.5 to 100, 4.5 to -200), find(0, 100, 0, 0, -200, 0, 50, 0, min = 60))
    }

    @Test
    fun `adjacent opposite-polarity samples are separate peaks not one plateau`() {
        // [0, 50, −50, 0]. Equal magnitudes, but they are different signed runs.
        // i = 1, s = +1: a = 0, b = 50, c = −50 → δ = 50 / (2·(0 − 100 − 50)) = −1/6 → 1.5 − 1/6.
        // i = 2, s = −1: a = −50, b = 50, c = 0 → δ = −50 / (2·(−50 − 100 + 0)) = +1/6 → 2.5 + 1/6.
        assertCandidates(listOf(1.5 - 1.0 / 6 to 50, 2.5 + 1.0 / 6 to -50), find(0, 50, -50, 0))
    }

    @Test
    fun `opposite-sign neighbour counts as weaker than zero`() {
        // [0, 30, −40, 30, 0]:
        // i = 1, s = +1: a = 0, b = 30, c = −40 → δ = 40 / (2·(0 − 60 − 40)) = −0.2 → 1.3.
        // i = 2, s = −1: a = c = −30, b = 40 → δ = 0 → 2.5.
        // i = 3, s = +1: a = −40, b = 30, c = 0 → δ = −40 / (2·(−40 − 60 + 0)) = +0.2 → 3.7.
        assertCandidates(listOf(1.3 to 30, 2.5 to -40, 3.7 to 30), find(0, 30, -40, 30, 0))
    }

    @Test
    fun `profile endpoints are never candidates`() {
        assertEquals(emptyList<EdgeCandidate>(), find(100, 0, 0, 0, -100))
        assertEquals(emptyList<EdgeCandidate>(), find(100, 50, 0))
        assertEquals(emptyList<EdgeCandidate>(), find(0, 50, 100))
        // Plateaus touching an endpoint have no outer neighbour on that side.
        assertEquals(emptyList<EdgeCandidate>(), find(80, 80, 0, 0))
        assertEquals(emptyList<EdgeCandidate>(), find(0, 0, -80, -80))
        assertEquals(emptyList<EdgeCandidate>(), find(7, 7, 7))
    }

    @Test
    fun `odd-length plateau is reported at its middle sample without interpolation`() {
        // Run [1, 3] → (1 + 3) / 2 + 0.5 = 2.5, regardless of unequal outer neighbours.
        assertCandidates(listOf(2.5 to 60), find(0, 60, 60, 60, 0))
        assertCandidates(listOf(2.5 to 60), find(0, 60, 60, 60, 30))
        assertCandidates(listOf(2.5 to -60), find(-59, -60, -60, -60, 10))
    }

    @Test
    fun `even-length plateau is reported halfway between its middle samples`() {
        // Run [1, 2] → 1.5 + 0.5 = 2.0;  run [1, 4] → 2.5 + 0.5 = 3.0.
        assertCandidates(listOf(2.0 to 60), find(0, 60, 60, 0))
        assertCandidates(listOf(3.0 to 7), find(0, 7, 7, 7, 7, 0))
        assertCandidates(listOf(3.0 to 7), find(6, 7, 7, 7, 7, -100))
    }

    @Test
    fun `plateau that is not a local maximum is rejected`() {
        // [0, 60, 60, 90, 0]: the plateau's right neighbour is stronger.
        // 90 at i = 3: a = 60, b = 90, c = 0 → δ = 60 / (2·(60 − 180)) = −0.25 → 3.25.
        assertCandidates(listOf(3.25 to 90), find(0, 60, 60, 90, 0))
        // Rising staircase: only the top step qualifies. 3 at i = 3: a = 2, c = 0 →
        // δ = 2 / (2·(2 − 6)) = −0.25 → 3.25.
        assertCandidates(listOf(3.25 to 3), find(0, 2, 2, 3, 0))
    }

    @Test
    fun `firstPixel offsets every position`() {
        assertCandidates(listOf(102.5 to 40), find(0, 10, 40, 10, 0, firstPixel = 100))
        assertCandidates(listOf(-0.5 to 40), find(0, 10, 40, 10, 0, firstPixel = -3))
        assertCandidates(listOf(12.0 to 60), find(0, 60, 60, 0, firstPixel = 10))
    }

    @Test
    fun `asymmetric strict peak shifts towards the stronger neighbour`() {
        // a = 20, b = 100, c = 60: δ = (20 − 60) / (2·(20 − 200 + 60)) = −40 / −240 = 1/6.
        assertCandidates(listOf(2.5 + 1.0 / 6 to 100), find(0, 20, 100, 60, 0))
        assertCandidates(listOf(2.5 - 1.0 / 6 to 100), find(0, 60, 100, 20, 0))
        // Same geometry for the negative polarity.
        assertCandidates(listOf(2.5 + 1.0 / 6 to -100), find(0, -20, -100, -60, 0))
    }

    @Test
    fun `nearly flat strict peak stays strictly inside the neighbouring sample interval`() {
        // a = 99, b = 100, c = 0: δ = 99 / (2·(99 − 200)) = −99/202 ≈ −0.4901.
        assertCandidates(listOf(2.5 - 99.0 / 202 to 100), find(0, 99, 100, 0, 0))
        // a = MAX − 1, b = MAX, c = 0: denominator −(MAX + 1) is far from zero in Long.
        val max = Int.MAX_VALUE
        val expected = 2.5 + (max - 1).toDouble() / (2.0 * ((max - 1).toDouble() - 2.0 * max))
        val got = find(0, max - 1, max, 0, 0, min = max)
        assertCandidates(listOf(expected to max), got)
        assertTrue(got.single().position > 2.0 && got.single().position < 2.5)
    }

    @Test
    fun `strict peaks stay within half a sample of their peak sample`() {
        val random = Random(20261003)
        repeat(2_000) {
            // Distinct values: every candidate is a single-sample peak.
            val profile = (-1000..1000).shuffled(random).take(9).toIntArray()
            for (c in EdgeCandidates.find(profile, 0, 0)) {
                val i = round(c.position - 0.5).toInt()
                assertEquals(c.response, profile[i])
                assertTrue("$c in ${profile.toList()}", c.position > i && c.position < i + 1.0)
            }
        }
    }

    @Test
    fun `ideal positive step maps to the exact source boundary`() {
        // Step between pixels 3 and 4: I = 20 for x ≤ 3, 120 for x ≥ 4 (all three rows equal).
        // Sobel dx(x) = 4·(I(x+1) − I(x−1)):
        //   x=1: 4·(20−20)=0  x=2: 0  x=3: 4·(120−20)=400  x=4: 4·(120−20)=400  x=5: 0  x=6: 0
        // Plateau on pixels 3 and 4 (centres 3.5, 4.5) → boundary 4.0.
        val row = intArrayOf(20, 20, 20, 20, 120, 120, 120, 120)
        val img = GrayImage(8, 3, row + row + row)
        val profile = Sobel.rowDx(img, 1, 1, 7)
        assertArrayEquals(intArrayOf(0, 0, 400, 400, 0, 0), profile)
        assertCandidates(listOf(4.0 to 400), EdgeCandidates.find(profile, 1, 100))
        // Same boundary from a sub-range starting at pixel 2.
        assertCandidates(listOf(4.0 to 400), EdgeCandidates.find(Sobel.rowDx(img, 1, 2, 6), 2, 100))
    }

    @Test
    fun `ideal negative step maps to the exact source boundary`() {
        // I = 120 for y ≤ 3, 20 for y ≥ 4 down a column; dy(y) = 4·(I(y+1) − I(y−1)):
        //   y=1: 0  y=2: 0  y=3: −400  y=4: −400  y=5: 0  y=6: 0 → boundary 4.0.
        val img = GrayImage(3, 8, IntArray(24) { if (it / 3 <= 3) 120 else 20 })
        val profile = Sobel.columnDy(img, 1, 1, 7)
        assertArrayEquals(intArrayOf(0, 0, -400, -400, 0, 0), profile)
        assertCandidates(listOf(4.0 to -400), EdgeCandidates.find(profile, 1, 100))
    }

    @Test
    fun `negative threshold is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { find(0, 1, 0, min = -1) }
        assertThrows(IllegalArgumentException::class.java) { find(0, 1, 0, min = Int.MIN_VALUE) }
    }

    @Test
    fun `Int MIN_VALUE responses are handled without overflow`() {
        val min = Int.MIN_VALUE
        // |MIN_VALUE| = 2^31 ≥ MAX_VALUE, so it passes even the largest threshold.
        assertCandidates(listOf(1.5 to min), find(0, min, 0, min = Int.MAX_VALUE))
        // s = −1: a = c = −(MIN + 1) = 2^31 − 1, b = 2^31 → symmetric, δ = 0 → 2.5.
        assertCandidates(listOf(2.5 to min), find(0, min + 1, min, min + 1, 0))
        // MIN_VALUE plateau: run [1, 2] → 2.0.
        assertCandidates(listOf(2.0 to min), find(0, min, min, 0))
        // MIN_VALUE as a neighbour of a positive peak counts as −2^31 (weaker), not +2^31.
        // i = 2, s = +1: a = −2^31, b = 1, c = 0 → δ = −2^31 / (2·(−2^31 − 2)), just under +0.5.
        // i = 1, s = −1: a = 0, b = 2^31, c = −1 → δ = 1 / (2·(−2^32 − 1)), a hair below zero.
        val deltaMin = 1.0 / (2.0 * (-4294967296.0 - 1.0))
        val deltaOne = -2147483648.0 / (2.0 * (-2147483648.0 - 2.0))
        assertCandidates(listOf(1.5 + deltaMin to min, 2.5 + deltaOne to 1), find(0, min, 1, 0))
    }
}
