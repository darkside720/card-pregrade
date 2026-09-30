package com.cardpregrade.app.ui.capture

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cardpregrade.app.camera.CameraSource
import com.cardpregrade.app.camera.CameraSourceState
import com.cardpregrade.app.camera.CaptureCameraInfo
import com.cardpregrade.app.camera.CaptureOutcome
import com.cardpregrade.app.capture.CaptureInspector
import com.cardpregrade.app.capture.CaptureStorage
import com.cardpregrade.app.capture.DeviceInfo
import com.cardpregrade.app.capture.InspectedCapture
import com.cardpregrade.core.data.repository.ScanRepository
import com.cardpregrade.core.data.repository.ScanSessionSummary
import com.cardpregrade.core.model.AlgorithmVersion
import com.cardpregrade.core.model.CaptureKind
import com.cardpregrade.core.model.CaptureProtocol
import com.cardpregrade.core.model.CapturedImage
import com.cardpregrade.core.model.Card
import com.cardpregrade.core.model.ImageOrientation
import com.cardpregrade.core.model.ImageQualityResult
import com.cardpregrade.core.model.ProcessingMode
import com.cardpregrade.core.model.QualityCheck
import com.cardpregrade.core.model.QualityMetric
import com.cardpregrade.core.model.QualityVerdict
import com.cardpregrade.core.model.ScanSession
import com.cardpregrade.core.model.ScanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class GuidedCaptureViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val sessionId = "11111111-2222-4333-8444-555555555555"

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    /** JVM fake: writes bytes instead of a real JPEG; the fake inspector supplies metadata. */
    private class TestCamera(var fail: Boolean = false) : CameraSource {
        override val state: StateFlow<CameraSourceState> = MutableStateFlow(CameraSourceState.Ready(torchSupported = false, cameraId = "cam-1"))
        var released = false

        @Composable
        override fun PreviewContent(modifier: Modifier) = Unit

        override suspend fun capture(target: File): CaptureOutcome {
            if (fail) return CaptureOutcome.Failure("Capture failed: simulated")
            target.writeText("jpeg-bytes")
            return CaptureOutcome.Success(target, CaptureCameraInfo("cam-1", false, 0f, 4000, 3000, 5.1f))
        }

        override fun focusAt(x: Float, y: Float) = Unit
        override fun setTorch(enabled: Boolean) = Unit
        override fun setExposureCompensationIndex(index: Int) = Unit
        override fun release() {
            released = true
        }
    }

    private class InMemoryScans(initial: List<CapturedImage> = emptyList()) : ScanRepository {
        val captures = initial.associateBy { it.kind }.toMutableMap()
        var status = ScanStatus.CAPTURING
        override fun observeSessions(): Flow<List<ScanSessionSummary>> = flowOf(emptyList())
        override suspend fun createSession(card: Card, protocol: CaptureProtocol, processingMode: ProcessingMode) = error("unused")
        override suspend fun getSession(id: String) =
            ScanSession(id, "card", Instant.EPOCH, status, "standard", 1, captures = captures.values.toList())
        override suspend fun updateStatus(sessionId: String, status: ScanStatus) { this.status = status }
        override suspend fun addCapture(image: CapturedImage) { captures[image.kind] = image }
        override suspend fun removeCapture(sessionId: String, kind: CaptureKind) { captures.remove(kind) }
        override suspend fun deleteSession(sessionId: String) = Unit
        override suspend fun latestCapturedSession(): ScanSession? = null
    }

    private var nextVerdict = QualityVerdict.GOOD

    private val inspector = CaptureInspector { id, _ ->
        InspectedCapture(
            storedWidth = 4000, storedHeight = 3000, orientation = ImageOrientation.fromExif(6),
            iso = 100, exposureTimeNs = 10_000_000, focalLengthMm = null, flashFired = false,
            quality = ImageQualityResult.fromChecks(
                id, listOf(QualityCheck(QualityMetric.SHARPNESS, nextVerdict, 12.0, null, "m")), AlgorithmVersion("q", "1"),
            ),
        )
    }

    private fun vm(camera: TestCamera = TestCamera(), scans: InMemoryScans = InMemoryScans()) = GuidedCaptureViewModel(
        sessionId = sessionId,
        scans = scans,
        storage = CaptureStorage(tmp.root),
        inspector = inspector,
        camera = camera,
        device = DeviceInfo("TestCo", "Phone", "15 (API 35)", "0.2.0"),
        clock = Clock.fixed(Instant.parse("2026-09-30T10:00:00Z"), ZoneOffset.UTC),
        io = dispatcher,
        compute = dispatcher,
    )

    private fun flow(vm: GuidedCaptureViewModel) = vm.state.value.flow!!

    @Test
    fun `capture failure shows error and stays on the step`() {
        val model = vm(TestCamera(fail = true))
        model.capture()
        assertEquals("Capture failed: simulated", model.state.value.error)
        assertEquals(StepStatus.PENDING, flow(model).current.status)
        assertFalse(model.state.value.busy)
        assertTrue("no orphan files", File(tmp.root, "scans/$sessionId/pending").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `successful capture records metadata and waits for review`() {
        val model = vm()
        model.capture()
        val candidate = flow(model).current.candidate!!
        assertEquals(StepStatus.REVIEWING, flow(model).current.status)
        assertEquals(CaptureKind.FRONT_STRAIGHT, candidate.kind)
        assertEquals(4000, candidate.widthPx)
        assertEquals(90, candidate.orientation.rotationDegrees)
        assertEquals("TestCo", candidate.device.manufacturer)
        assertEquals("cam-1", candidate.device.cameraId)
        assertEquals(100, candidate.device.iso)
        assertEquals(5.1f, candidate.device.lensFocalLengthMm)
        assertEquals(Instant.parse("2026-09-30T10:00:00Z"), candidate.capturedAt)
    }

    @Test
    fun `accept persists file and metadata then advances`() {
        val scans = InMemoryScans()
        val model = vm(scans = scans)
        model.capture()
        model.accept(override = false)

        val stored = scans.captures[CaptureKind.FRONT_STRAIGHT]!!
        assertEquals("scans/$sessionId/front-straight.jpg", stored.localUri)
        assertTrue(File(tmp.root, stored.localUri).exists())
        assertFalse(stored.qualityOverridden)
        assertEquals(CaptureKind.BACK_STRAIGHT, flow(model).current.step.kind)
    }

    @Test
    fun `warning override is recorded in persisted metadata`() {
        nextVerdict = QualityVerdict.RETAKE_RECOMMENDED
        val scans = InMemoryScans()
        val model = vm(scans = scans)
        model.capture()
        model.accept(override = false)
        assertNull("not accepted without override", scans.captures[CaptureKind.FRONT_STRAIGHT])
        model.accept(override = true)
        assertTrue(scans.captures[CaptureKind.FRONT_STRAIGHT]!!.qualityOverridden)
    }

    @Test
    fun `retake of an accepted photo removes file and record`() {
        val scans = InMemoryScans()
        val model = vm(scans = scans)
        model.capture(); model.accept(false)
        model.retake(CaptureKind.FRONT_STRAIGHT)
        assertNull(scans.captures[CaptureKind.FRONT_STRAIGHT])
        assertFalse(File(tmp.root, "scans/$sessionId/front-straight.jpg").exists())
        assertEquals(StepStatus.PENDING, flow(model).current.status)
    }

    @Test
    fun `four accepted photos complete the session`() {
        val scans = InMemoryScans()
        val model = vm(scans = scans)
        repeat(4) { model.capture(); model.accept(false) }
        assertTrue(flow(model).isComplete)
        assertEquals(ScanStatus.CAPTURED, scans.status)
        assertEquals(CaptureProtocol.V1_STANDARD.steps.map { it.kind }.toSet(), scans.captures.keys)
        listOf("front-straight", "back-straight", "front-left", "front-right").forEach {
            assertTrue("$it.jpg missing", File(tmp.root, "scans/$sessionId/$it.jpg").exists())
        }
    }

    @Test
    fun `new view model restores persisted progress`() {
        val scans = InMemoryScans()
        val first = vm(scans = scans)
        first.capture(); first.accept(false)
        first.capture(); first.accept(false)

        val restored = vm(scans = scans)
        assertEquals(2, flow(restored).capturedCount)
        assertEquals(CaptureKind.FRONT_ANGLE_LEFT, flow(restored).current.step.kind)
        assertNotNull(flow(restored).captures[0].accepted)
    }
}
