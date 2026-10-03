package com.cardpregrade.app.capture

import com.cardpregrade.core.cv.image.GrayImage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expected sizes are worked by hand; the short side is round(short · 1024 / long), halves up. */
class WorkingImageSizingTest {

    @Test
    fun `sample size is the largest power of two keeping the long side at or above the target`() {
        // Pixel 4080: 4080/2 = 2040 ≥ 1024, 4080/4 = 1020 < 1024 → 2.
        assertEquals(2, WorkingImageSizing.sampleSize(4080))
        // 4096/4 = 1024 ≥ 1024 (exactly the target), 4096/8 = 512 → 4.
        assertEquals(4, WorkingImageSizing.sampleSize(4096))
        // 4095/4 = 1023 < 1024 → 2.
        assertEquals(2, WorkingImageSizing.sampleSize(4095))
        assertEquals(1, WorkingImageSizing.sampleSize(2047))
        assertEquals(2, WorkingImageSizing.sampleSize(2048))
        assertEquals(1, WorkingImageSizing.sampleSize(1024))
        assertEquals(1, WorkingImageSizing.sampleSize(800))
        assertEquals(1, WorkingImageSizing.sampleSize(1))
        // 8000 / 4 = 2000, / 8 = 1000 < 1024 → 4.
        assertEquals(4, WorkingImageSizing.sampleSize(8000))
        assertEquals(1 shl 20, WorkingImageSizing.sampleSize(Int.MAX_VALUE, 1024))
    }

    @Test
    fun `decoded long side never drops below the target`() {
        for (long in listOf(1024, 1025, 2047, 2048, 3000, 4080, 4096, 6000, 9999, 100_000)) {
            val s = WorkingImageSizing.sampleSize(long)
            assertTrue("$long / $s", long / s >= 1024)
            assertTrue("$long / ${2 * s} should be below target", long / (2 * s) < 1024)
        }
    }

    @Test
    fun `pixel capture decoded at half size scales to exactly 1024 by 771`() {
        // 4080 × 3072 stored, sample 2 → 2040 × 1536. Short: (1536 · 1024 + 1020) / 2040
        // = 1 573 884 / 2040 = 771 (771.51 floored; exact ratio 771.01).
        assertEquals(1024 to 771, WorkingImageSizing.scaledSize(2040, 1536))
        // Same capture stored portrait.
        assertEquals(771 to 1024, WorkingImageSizing.scaledSize(1536, 2040))
    }

    @Test
    fun `landscape and portrait sources keep their aspect ratio`() {
        // 2048 × 1536 (4:3): 1536 · 1024 / 2048 = 768 exactly.
        assertEquals(1024 to 768, WorkingImageSizing.scaledSize(2048, 1536))
        assertEquals(768 to 1024, WorkingImageSizing.scaledSize(1536, 2048))
        // 1920 × 1080: (1080 · 1024 + 960) / 1920 = 1 106 880 / 1920 = 576.5 → 576 (exact 576.0).
        assertEquals(1024 to 576, WorkingImageSizing.scaledSize(1920, 1080))
    }

    @Test
    fun `sources at or below the target are never upscaled`() {
        assertEquals(1024 to 768, WorkingImageSizing.scaledSize(1024, 768))
        assertEquals(800 to 600, WorkingImageSizing.scaledSize(800, 600))
        assertEquals(1 to 1, WorkingImageSizing.scaledSize(1, 1))
        assertEquals(1024 to 1024, WorkingImageSizing.scaledSize(1024, 1024))
    }

    @Test
    fun `odd dimensions round half up deterministically`() {
        // 4081 × 3071: (3071 · 1024 + 2040) / 4081 = 3 146 744 / 4081 = 771 (remainder 293).
        assertEquals(1024 to 771, WorkingImageSizing.scaledSize(4081, 3071))
        // 2049 × 1025: (1025 · 1024 + 1024) / 2049 = 1 050 624 / 2049 = 512 (exact 512.25).
        assertEquals(1024 to 512, WorkingImageSizing.scaledSize(2049, 1025))
        // 2000 × 1001: 1001 · 1024 / 2000 = 512.512 → (1 025 024 + 1000) / 2000 = 513.
        assertEquals(1024 to 513, WorkingImageSizing.scaledSize(2000, 1001))
        // Exactly half-way: 2048 × 3 → 3 · 1024 / 2048 = 1.5 → 2 (halves up).
        assertEquals(1024 to 2, WorkingImageSizing.scaledSize(2048, 3))
        // Square input keeps both sides equal.
        assertEquals(1024 to 1024, WorkingImageSizing.scaledSize(1025, 1025))
        repeat(3) { assertEquals(1024 to 771, WorkingImageSizing.scaledSize(4081, 3071)) }
    }

    @Test
    fun `extreme aspect ratios never produce a zero-pixel side`() {
        // 100 000 × 1: 1 · 1024 / 100 000 = 0.01 → 0 → clamped to 1.
        assertEquals(1024 to 1, WorkingImageSizing.scaledSize(100_000, 1))
        assertEquals(1 to 1024, WorkingImageSizing.scaledSize(1, 100_000))
        // 10 000 × 3: 0.307 → 1.
        assertEquals(1024 to 1, WorkingImageSizing.scaledSize(10_000, 3))
        // Products stay in Long: Int.MAX_VALUE × Int.MAX_VALUE → 1024 × 1024.
        assertEquals(1024 to 1024, WorkingImageSizing.scaledSize(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test
    fun `invalid sizes are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { WorkingImageSizing.sampleSize(0) }
        assertThrows(IllegalArgumentException::class.java) { WorkingImageSizing.sampleSize(100, 0) }
        assertThrows(IllegalArgumentException::class.java) { WorkingImageSizing.scaledSize(0, 10) }
        assertThrows(IllegalArgumentException::class.java) { WorkingImageSizing.scaledSize(10, -1) }
        assertThrows(IllegalArgumentException::class.java) { WorkingImageSizing.scaledSize(10, 10, 0) }
    }

    @Test
    fun `ARGB pixels convert to BT601 luma with alpha ignored`() {
        // (299·R + 587·G + 114·B) / 1000, integer division:
        // white 255, black 0, red 76 (76.245), green 149 (149.685), blue 29 (29.07),
        // card border F2D14A = (72 358 + 122 683 + 8 436) / 1000 = 203, transparent white 255.
        val argb = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFF0000.toInt(),
            0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFF2D14A.toInt(),
            0x00FFFFFF, 0x80000000.toInt(), 0xFF202020.toInt(),
        )
        val gray = GrayImage.fromArgb(3, 3, argb)
        assertEquals(3, gray.width)
        assertEquals(3, gray.height)
        assertArrayEquals(intArrayOf(255, 0, 76, 149, 29, 203, 255, 0, 32), gray.luma)
        // Row-major: pixel (2, 1) is index 5.
        assertEquals(203, gray[2, 1])
    }
}
