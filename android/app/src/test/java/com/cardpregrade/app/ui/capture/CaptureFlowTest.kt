package com.cardpregrade.app.ui.capture

import com.cardpregrade.core.model.AlgorithmVersion
import com.cardpregrade.core.model.CaptureKind
import com.cardpregrade.core.model.CaptureProtocol
import com.cardpregrade.core.model.CapturedImage
import com.cardpregrade.core.model.DeviceMetadata
import com.cardpregrade.core.model.ImageQualityResult
import com.cardpregrade.core.model.QualityCheck
import com.cardpregrade.core.model.QualityMetric
import com.cardpregrade.core.model.QualityVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CaptureFlowTest {

    private val v = AlgorithmVersion("test", "1")

    private fun quality(verdict: QualityVerdict) = ImageQualityResult.fromChecks(
        "img", listOf(QualityCheck(QualityMetric.SHARPNESS, verdict, 1.0, null, "m")), v,
    )

    private fun image(kind: CaptureKind, verdict: QualityVerdict = QualityVerdict.GOOD) = CapturedImage(
        id = "id-$kind", sessionId = "s", kind = kind, localUri = "scans/s/pending/x.jpg",
        widthPx = 4000, heightPx = 3000, capturedAt = Instant.EPOCH,
        device = DeviceMetadata("m", "d", "15", "0.2"), quality = quality(verdict),
    )

    private fun start() = CaptureFlowState.start(CaptureProtocol.V1_STANDARD)

    private fun CaptureFlowState.captureAndAccept(verdict: QualityVerdict = QualityVerdict.GOOD) =
        onCaptured(image(current.step.kind, verdict)).accept(override = verdict != QualityVerdict.GOOD)!!

    @Test
    fun `capture waits for review and never auto-advances`() {
        val s = start().onCaptured(image(CaptureKind.FRONT_STRAIGHT))
        assertEquals(StepStatus.REVIEWING, s.current.status)
        assertEquals(CaptureKind.FRONT_STRAIGHT, s.current.step.kind)
        assertEquals(0, s.capturedCount)
    }

    @Test
    fun `accepting a good photo stores it and advances`() {
        val s = start().onCaptured(image(CaptureKind.FRONT_STRAIGHT)).accept(override = false)!!
        assertEquals(StepStatus.CAPTURED, s.captures[0].status)
        assertFalse(s.captures[0].accepted!!.qualityOverridden)
        assertEquals(CaptureKind.BACK_STRAIGHT, s.current.step.kind)
    }

    @Test
    fun `warning requires an explicit override which is recorded`() {
        val reviewing = start().onCaptured(image(CaptureKind.FRONT_STRAIGHT, QualityVerdict.RETAKE_RECOMMENDED))
        assertFalse(reviewing.canAcceptDirectly)
        assertNull("no silent acceptance", reviewing.accept(override = false))
        val accepted = reviewing.accept(override = true)!!
        assertTrue(accepted.captures[0].accepted!!.qualityOverridden)
    }

    @Test
    fun `unusable photo cannot be accepted even with override`() {
        val bad = ImageQualityResult.fromChecks(
            "img", listOf(QualityCheck(QualityMetric.DECODE, QualityVerdict.UNUSABLE, null, null, "bad")), v,
        )
        val s = start().onCaptured(image(CaptureKind.FRONT_STRAIGHT).copy(quality = bad))
        assertEquals(StepStatus.UNUSABLE, s.current.status)
        assertNull(s.accept(override = true))
    }

    @Test
    fun `retake discards candidate or accepted photo`() {
        val reviewing = start().onCaptured(image(CaptureKind.FRONT_STRAIGHT))
        val retaken = reviewing.retake(CaptureKind.FRONT_STRAIGHT)
        assertEquals(StepStatus.PENDING, retaken.current.status)
        assertNull(retaken.current.candidate)

        val accepted = start().captureAndAccept().retake(CaptureKind.FRONT_STRAIGHT)
        assertEquals(CaptureKind.FRONT_STRAIGHT, accepted.current.step.kind)
        assertNull(accepted.current.accepted)
        assertEquals(0, accepted.capturedCount)
    }

    @Test
    fun `four step progression associates each photo with its capture type`() {
        var s = start()
        repeat(4) { s = s.captureAndAccept() }
        assertTrue(s.isComplete)
        assertEquals(
            listOf(CaptureKind.FRONT_STRAIGHT, CaptureKind.BACK_STRAIGHT, CaptureKind.FRONT_ANGLE_LEFT, CaptureKind.FRONT_ANGLE_RIGHT),
            s.captures.map { it.accepted!!.kind },
        )
        s.captures.forEach { assertEquals(it.step.kind, it.accepted!!.kind) }
    }

    @Test
    fun `captured image must match the current step`() {
        assertThrows(IllegalArgumentException::class.java) { start().onCaptured(image(CaptureKind.BACK_STRAIGHT)) }
    }

    @Test
    fun `restore rebuilds progress from persisted photos`() {
        val restored = CaptureFlowState.restore(
            CaptureProtocol.V1_STANDARD,
            listOf(image(CaptureKind.FRONT_STRAIGHT), image(CaptureKind.FRONT_ANGLE_LEFT)),
        )
        assertEquals(2, restored.capturedCount)
        assertEquals(CaptureKind.BACK_STRAIGHT, restored.current.step.kind)
        assertNotNull(restored.captures[2].accepted)
        assertFalse(restored.isComplete)
    }

    @Test
    fun `angled steps carry directional guidance`() {
        val directions = CaptureProtocol.V1_STANDARD.steps.map { it.direction.name }
        assertEquals(listOf("NONE", "NONE", "ROTATE_LEFT", "ROTATE_RIGHT"), directions)
    }
}
