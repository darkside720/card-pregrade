package com.cardpregrade.core.data.mapping

import com.cardpregrade.core.data.db.InspectionResultEntity
import com.cardpregrade.core.model.AlgorithmVersion
import com.cardpregrade.core.model.CapturedImage
import com.cardpregrade.core.model.ImageOrientation
import com.cardpregrade.core.model.ImageQualityResult
import com.cardpregrade.core.model.QualityCheck
import com.cardpregrade.core.model.QualityMetric
import com.cardpregrade.core.model.QualityVerdict
import com.cardpregrade.core.model.Card
import com.cardpregrade.core.model.CardGame
import com.cardpregrade.core.model.CardIdentification
import com.cardpregrade.core.model.CaptureKind
import com.cardpregrade.core.model.Confidence
import com.cardpregrade.core.model.DeviceMetadata
import com.cardpregrade.core.model.IdentificationSource
import com.cardpregrade.core.model.LightingAngle
import com.cardpregrade.core.model.LightingMetadata
import com.cardpregrade.core.model.ProcessingMode
import com.cardpregrade.core.model.ScanSession
import com.cardpregrade.core.model.ScanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class MappersTest {

    private val now = Instant.ofEpochMilli(1_790_000_000_000)

    @Test
    fun `card round trips with and without identification`() {
        val unknown = Card(id = "c1", game = CardGame.UNKNOWN)
        assertEquals(unknown, unknown.toEntity().toDomain())

        val identified = Card(
            id = "c2",
            game = CardGame.ONE_PIECE,
            identification = CardIdentification(
                game = CardGame.ONE_PIECE, setCode = "OP01", cardNumber = "OP01-120", cardName = "Shanks",
                language = "EN", source = IdentificationSource.AUTOMATIC, confidence = Confidence(0.7),
            ),
        )
        assertEquals(identified, identified.toEntity().toDomain())
    }

    @Test
    fun `session round trips`() {
        val session = ScanSession("s1", "c1", now, ScanStatus.CAPTURING, "standard", 1, ProcessingMode.LOCAL_ONLY)
        assertEquals(session, session.toEntity().toDomain())
    }

    @Test
    fun `captured image keeps device and lighting metadata`() {
        val image = CapturedImage(
            id = "i1", sessionId = "s1", kind = CaptureKind.FRONT_ANGLE_LEFT, localUri = "file:///data/img.jpg",
            widthPx = 4000, heightPx = 3000, capturedAt = now,
            device = DeviceMetadata("Google", "Pixel 9", "15", "0.1.0", cameraId = "0", iso = 100),
            lighting = LightingMetadata(LightingAngle.LEFT),
        )
        assertEquals(image, image.toEntity().toDomain())
    }

    @Test
    fun `captured image with quality checks orientation and override round trips`() {
        val quality = ImageQualityResult.fromChecks(
            sourceImageId = "i2",
            checks = listOf(
                QualityCheck(QualityMetric.RESOLUTION, QualityVerdict.GOOD, 3000.0, "px", "ok"),
                QualityCheck(QualityMetric.SHARPNESS, QualityVerdict.RETAKE_RECOMMENDED, 21.5, "Laplacian variance", "blur"),
            ),
            algorithmVersion = AlgorithmVersion("capture-quality", "1"),
            blurScore = 0.215,
            resolutionScore = 1.0,
        )
        val image = CapturedImage(
            id = "i2", sessionId = "s1", kind = CaptureKind.BACK_STRAIGHT, localUri = "scans/s1/back-straight.jpg",
            widthPx = 4000, heightPx = 3000, capturedAt = now,
            device = DeviceMetadata("Google", "Pixel 9", "15 (API 35)", "0.2.0", cameraId = "0", iso = 200,
                exposureTimeNs = 16_666_666, lensFocalLengthMm = 6.9f, flashUsed = false, exposureCompensationEv = -0.5f, torchOn = false),
            quality = quality,
            orientation = ImageOrientation.fromExif(6),
            qualityOverridden = true,
        )
        val entity = image.toEntity()
        assertEquals(90, entity.rotationDegrees)
        assertEquals("RETAKE_RECOMMENDED", entity.qualityVerdict)
        val checks = image.qualityCheckEntities()
        assertEquals(2, checks.size)
        assertEquals(image, entity.toDomain(checks))
    }

    @Test
    fun `summary formats stored half point ranges`() {
        val entity = InspectionResultEntity(
            id = "r1", sessionId = "s1", cardId = "c1", cardLabel = "Unknown Card", createdAtMillis = 0,
            psaLowHalfPoints = 18, psaHighHalfPoints = 20, bgsLowHalfPoints = null, bgsHighHalfPoints = null,
            overallConfidence = 0.82, defectCount = 3, warningCount = 1, pipelineVersion = "p1",
            gradingRuleSetVersion = "r@1", processingMode = "LOCAL_ONLY", payloadJson = null,
        )
        val summary = entity.toSummary()
        assertEquals("9–10", summary.psaRangeLabel)
        assertNull(summary.bgsRangeLabel)
        assertEquals(82, summary.overallConfidencePercent)
    }
}
