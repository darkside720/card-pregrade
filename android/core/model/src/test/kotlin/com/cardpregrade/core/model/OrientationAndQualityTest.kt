package com.cardpregrade.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OrientationAndQualityTest {

    @Test
    fun `EXIF orientation maps to clockwise rotation`() {
        assertEquals(0, ImageOrientation.fromExif(1).rotationDegrees)
        assertEquals(90, ImageOrientation.fromExif(6).rotationDegrees)
        assertEquals(180, ImageOrientation.fromExif(3).rotationDegrees)
        assertEquals(270, ImageOrientation.fromExif(8).rotationDegrees)
        assertEquals(0, ImageOrientation.fromExif(null).rotationDegrees)
        assertTrue(ImageOrientation.fromExif(5).mirrored)
        assertFalse(ImageOrientation.fromExif(6).mirrored)
    }

    @Test
    fun `rotated sensor image has portrait oriented size`() {
        val image = CapturedImage(
            id = "i", sessionId = "s", kind = CaptureKind.FRONT_STRAIGHT, localUri = "scans/s/front-straight.jpg",
            widthPx = 4000, heightPx = 3000, capturedAt = Instant.EPOCH,
            device = DeviceMetadata("m", "d", "15", "0.1"),
            orientation = ImageOrientation.fromExif(6),
        )
        assertEquals(3000 to 4000, image.orientedSize)
        assertEquals(CardSide.FRONT, image.side)
        assertEquals(4000 to 3000, image.copy(orientation = ImageOrientation.fromExif(1)).orientedSize)
    }

    private val v = AlgorithmVersion("t", "1")

    @Test
    fun `soft verdicts produce non-blocking warnings and stay acceptable`() {
        val result = ImageQualityResult.fromChecks(
            "img",
            listOf(
                QualityCheck(QualityMetric.RESOLUTION, QualityVerdict.GOOD, 3000.0, "px", "ok"),
                QualityCheck(QualityMetric.SHARPNESS, QualityVerdict.RETAKE_RECOMMENDED, 12.0, null, "blur"),
                QualityCheck(QualityMetric.EXPOSURE, QualityVerdict.WARNING, 60.0, null, "dark"),
            ),
            v,
        )
        assertTrue(result.acceptable)
        assertEquals(QualityVerdict.RETAKE_RECOMMENDED, result.verdict)
        assertEquals(listOf(ImageQualityIssue.BLUR, ImageQualityIssue.UNDEREXPOSED), result.warnings.map { it.type })
        assertTrue(result.warnings.none { it.blocking })
    }

    @Test
    fun `unusable verdict is blocking`() {
        val result = ImageQualityResult.fromChecks(
            "img", listOf(QualityCheck(QualityMetric.DECODE, QualityVerdict.UNUSABLE, null, null, "bad")), v,
        )
        assertFalse(result.acceptable)
        assertEquals(QualityVerdict.UNUSABLE, result.verdict)
        assertTrue(result.warnings.single().blocking)
    }
}
