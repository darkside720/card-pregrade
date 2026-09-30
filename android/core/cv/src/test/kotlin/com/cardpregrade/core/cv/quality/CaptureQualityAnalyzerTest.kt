package com.cardpregrade.core.cv.quality

import com.cardpregrade.core.cv.image.GrayImage
import com.cardpregrade.core.model.QualityMetric
import com.cardpregrade.core.model.QualityVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureQualityAnalyzerTest {

    private val analyzer = CaptureQualityAnalyzer()
    private val w = 1024
    private val h = 768

    /** High-contrast 8 px checkerboard around mid-grey: very sharp, well exposed, no clipping. */
    private fun sharp(): GrayImage = GrayImage(w, h, IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        if ((x / 8 + y / 8) % 2 == 0) 90 else 170
    })

    private fun uniform(value: Int) = GrayImage(w, h, IntArray(w * h) { value })

    /** Same checkerboard after a wide box blur: edges become gradual ramps → low Laplacian variance. */
    private fun blurred(): GrayImage {
        val src = sharp()
        val r = 6
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0
            var n = 0
            for (dy in -r..r) for (dx in -r..r) {
                val xx = (x + dx).coerceIn(0, w - 1)
                val yy = (y + dy).coerceIn(0, h - 1)
                s += src[xx, yy]; n++
            }
            out[y * w + x] = s / n
        }
        return GrayImage(w, h, out)
    }

    private fun verdict(result: com.cardpregrade.core.model.ImageQualityResult, metric: QualityMetric) =
        result.checks.single { it.metric == metric }.verdict

    @Test
    fun `good capture is GOOD on every check`() {
        val result = analyzer.analyze("img", 4000, 3000, sharp())
        assertTrue(result.acceptable)
        assertEquals(QualityVerdict.GOOD, result.verdict)
        assertEquals(4, result.checks.size)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `resolution thresholds`() {
        assertEquals(QualityVerdict.GOOD, verdict(analyzer.analyze("i", 3200, 2400, sharp()), QualityMetric.RESOLUTION))
        assertEquals(QualityVerdict.WARNING, verdict(analyzer.analyze("i", 2000, 1500, sharp()), QualityMetric.RESOLUTION))
        assertEquals(QualityVerdict.RETAKE_RECOMMENDED, verdict(analyzer.analyze("i", 1280, 960, sharp()), QualityMetric.RESOLUTION))
        val tiny = analyzer.analyze("i", 640, 480, sharp())
        assertEquals(QualityVerdict.UNUSABLE, verdict(tiny, QualityMetric.RESOLUTION))
        assertFalse("catastrophic resolution is the one hard rejection", tiny.acceptable)
        assertEquals(480.0, tiny.checks.first { it.metric == QualityMetric.RESOLUTION }.measuredValue!!, 0.0)
    }

    @Test
    fun `resolution check uses the short side regardless of orientation`() {
        val landscape = analyzer.analyze("i", 4000, 3000, sharp())
        val portrait = analyzer.analyze("i", 3000, 4000, sharp())
        assertEquals(verdict(landscape, QualityMetric.RESOLUTION), verdict(portrait, QualityMetric.RESOLUTION))
    }

    @Test
    fun `blur lowers sharpness verdict but never blocks`() {
        val sharpVar = ImageMeasures.laplacianVariance(sharp(), sharp().centralRegion(0.6))
        val blurVar = ImageMeasures.laplacianVariance(blurred(), blurred().centralRegion(0.6))
        assertTrue("blur must reduce Laplacian variance ($blurVar vs $sharpVar)", blurVar < sharpVar / 10)

        val result = analyzer.analyze("img", 4000, 3000, blurred())
        assertEquals(QualityVerdict.RETAKE_RECOMMENDED, verdict(result, QualityMetric.SHARPNESS))
        assertTrue("soft problems stay overridable", result.acceptable)
    }

    @Test
    fun `featureless frame has zero Laplacian variance`() {
        assertEquals(0.0, ImageMeasures.laplacianVariance(uniform(128), uniform(128).centralRegion(0.6)), 1e-9)
    }

    @Test
    fun `exposure thresholds`() {
        assertEquals(QualityVerdict.RETAKE_RECOMMENDED, verdict(analyzer.analyze("i", 4000, 3000, uniform(20)), QualityMetric.EXPOSURE))
        assertEquals(QualityVerdict.WARNING, verdict(analyzer.analyze("i", 4000, 3000, uniform(55)), QualityMetric.EXPOSURE))
        assertEquals(QualityVerdict.GOOD, verdict(analyzer.analyze("i", 4000, 3000, uniform(128)), QualityMetric.EXPOSURE))
        assertEquals(QualityVerdict.WARNING, verdict(analyzer.analyze("i", 4000, 3000, uniform(200)), QualityMetric.EXPOSURE))
        assertEquals(QualityVerdict.RETAKE_RECOMMENDED, verdict(analyzer.analyze("i", 4000, 3000, uniform(235)), QualityMetric.EXPOSURE))
    }

    @Test
    fun `dark and bright map to the right issue type`() {
        val dark = analyzer.analyze("i", 4000, 3000, uniform(20))
        assertTrue(dark.warnings.any { it.type == com.cardpregrade.core.model.ImageQualityIssue.UNDEREXPOSED })
        val bright = analyzer.analyze("i", 4000, 3000, uniform(235))
        assertTrue(bright.warnings.any { it.type == com.cardpregrade.core.model.ImageQualityIssue.OVEREXPOSED })
    }

    @Test
    fun `highlight clipping is reported as possible glare`() {
        val img = sharp()
        // Blow out a 100x100 patch in the middle: 10,000 of 282,440 central pixels ≈ 3.5%.
        for (y in 334 until 434) for (x in 462 until 562) img.luma[y * w + x] = 255
        val result = analyzer.analyze("i", 4000, 3000, img)
        assertEquals(QualityVerdict.WARNING, verdict(result, QualityMetric.HIGHLIGHT_CLIPPING))
        assertTrue(result.warnings.any { it.type == com.cardpregrade.core.model.ImageQualityIssue.GLARE && !it.blocking })
    }

    @Test
    fun `undecodable image is the other hard rejection`() {
        val result = analyzer.analyze("img", 0, 0, null)
        assertFalse(result.acceptable)
        assertEquals(QualityVerdict.UNUSABLE, result.verdict)
        assertEquals(QualityMetric.DECODE, result.checks.single().metric)
        assertNull(result.blurScore)
    }

    @Test
    fun `analysis is deterministic`() {
        assertEquals(analyzer.analyze("i", 4000, 3000, blurred()), analyzer.analyze("i", 4000, 3000, blurred()))
    }

    @Test
    fun `pixel density estimate`() {
        // 3000 px short side × 0.745 / 63 mm ≈ 35.5 px/mm
        assertEquals(35.5, analyzer.estimatedPixelsPerMm(3000), 0.1)
    }

    @Test
    fun `luma conversion from ARGB`() {
        val img = GrayImage.fromArgb(2, 1, intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()))
        assertEquals(255, img[0, 0])
        assertEquals(0, img[1, 0])
    }
}
