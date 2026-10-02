package com.cardpregrade.app.capture

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardpregrade.app.FakeCameraPermission
import com.cardpregrade.app.MainActivity
import com.cardpregrade.app.TestContainer
import com.cardpregrade.app.camera.CameraSource
import com.cardpregrade.app.camera.CaptureOutcome
import com.cardpregrade.app.camera.FakeCameraSource
import com.cardpregrade.app.camera.FakeScene
import com.cardpregrade.app.camera.PermissionStatus
import com.cardpregrade.app.captureAndAccept
import com.cardpregrade.app.clickTag
import com.cardpregrade.app.clickText
import com.cardpregrade.app.startNewScan
import com.cardpregrade.app.waitForTag
import com.cardpregrade.app.waitForText
import com.cardpregrade.core.model.CaptureKind
import com.cardpregrade.core.model.LightingAngle
import com.cardpregrade.core.model.QualityVerdict
import com.cardpregrade.core.model.ScanSession
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Guided capture end to end with [FakeCameraSource] — no camera hardware involved.
 * Real JPEG files, EXIF, the quality analyzer, Room and app-private storage are all exercised.
 */
@RunWith(AndroidJUnit4::class)
class CaptureFlowInstrumentedTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val container get() = TestContainer.container
    private val filesDir: File get() = rule.activity.filesDir

    @After
    fun tearDown() = TestContainer.reset()

    private fun latestSession(): ScanSession = runBlocking { container.scanRepository.latestCapturedSession()!! }

    @Test
    fun permissionDeniedShowsExplanationAndNoCapture() {
        val permission = FakeCameraPermission(granted = false, resultOnRequest = PermissionStatus.DENIED)
        TestContainer.install(FakeCameraSource(), permission)
        rule.startNewScan()
        rule.onNodeWithTag("permission_panel").assertIsDisplayed()
        rule.clickTag("permission_request")
        rule.onNodeWithTag("permission_denied").assertIsDisplayed()
        assertEquals(1, permission.requests)
        assertTrue(rule.onAllNodesWithTag("capture_button").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun permanentlyDeniedOffersSettings() {
        TestContainer.install(FakeCameraSource(), FakeCameraPermission(false, PermissionStatus.PERMANENTLY_DENIED))
        rule.startNewScan()
        rule.clickTag("permission_request")
        rule.onNodeWithText("Open app settings").assertIsDisplayed()
    }

    @Test
    fun permissionGrantedShowsPreviewAndCapture() {
        TestContainer.install(FakeCameraSource(), FakeCameraPermission(granted = false, resultOnRequest = PermissionStatus.GRANTED))
        rule.startNewScan()
        rule.clickTag("permission_request")
        rule.waitForTag("capture_button")
        rule.onNodeWithTag("fake_preview", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("capture_button").performScrollTo().assertIsEnabled()
    }

    @Test
    fun cameraUnavailableIsReported() {
        TestContainer.install(FakeCameraSource(available = false))
        rule.startNewScan()
        rule.waitForTag("camera_unavailable")
        rule.onNodeWithTag("capture_button").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun captureFailureShowsErrorAndStaysOnStep() {
        TestContainer.install(FakeCameraSource(scene = FakeScene.FAIL))
        rule.startNewScan()
        rule.waitForTag("capture_button")
        rule.clickTag("capture_button")
        rule.waitForTag("capture_error")
        rule.onNodeWithTag("capture_error").performScrollTo().assertIsDisplayed()
        // The tag is on the Surface container; the message is a descendant Text node.
        rule.onNode(hasAnyAncestor(hasTestTag("capture_error")) and hasText("simulated camera error", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("Step 1 of 4", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("status_FRONT_STRAIGHT", useUnmergedTree = true).assertTextContains("Not captured")
    }

    @Test
    fun successfulCaptureShowsReviewAndDoesNotAutoAdvance() {
        TestContainer.install(FakeCameraSource(scene = FakeScene.GOOD))
        rule.startNewScan()
        rule.waitForTag("capture_button")
        rule.clickTag("capture_button")
        rule.waitForTag("quality_panel")
        rule.onNodeWithTag("quality_summary").assertTextContains("Photo looks usable.")
        rule.onNodeWithText("Step 1 of 4", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("accept_button").performScrollTo().assertTextContains("Accept")
        // Upright size reflects the EXIF rotation of the landscape sensor image.
        rule.onNodeWithTag("capture_resolution").assertTextContains("2400 × 3200 px (upright)", substring = true)
    }

    @Test
    fun retakeDiscardsCandidate() {
        val fake = FakeCameraSource()
        val camera = RecordingCameraSource(fake)
        TestContainer.install(fake)
        container.cameraSourceFactory = { camera }
        rule.startNewScan()
        rule.waitForTag("capture_button")
        rule.clickTag("capture_button")
        rule.waitForTag("retake_button")

        // The app chose this target: <filesDir>/scans/<this session>/pending/<candidate>.jpg.
        // Checking only this session keeps the test independent of other sessions on the device.
        val candidate = camera.targets.single()
        val pendingDir = candidate.parentFile!!
        assertEquals("pending", pendingDir.name)
        assertEquals(File(filesDir, "scans").canonicalFile, pendingDir.parentFile!!.parentFile!!.canonicalFile)
        assertTrue("candidate should exist before retake", candidate.isFile)

        rule.clickTag("retake_button")
        rule.waitForTag("capture_button")
        rule.waitUntil(5_000) { !candidate.exists() && pendingDir.listFiles().orEmpty().none { it.isFile } }
        rule.onNodeWithText("Step 1 of 4", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("status_FRONT_STRAIGHT", useUnmergedTree = true).assertTextContains("Not captured")
        rule.onNodeWithTag("capture_button").performScrollTo().assertIsEnabled()
    }

    @Test
    fun fourStepCapturePersistsFilesMetadataAndOrientation() {
        val camera = FakeCameraSource()
        TestContainer.install(camera)
        rule.startNewScan()
        repeat(4) { rule.captureAndAccept() }
        rule.onNodeWithTag("continue_button").performScrollTo().assertIsEnabled()

        val session = latestSession()
        assertEquals(4, session.captures.size)
        assertEquals(
            setOf(CaptureKind.FRONT_STRAIGHT, CaptureKind.BACK_STRAIGHT, CaptureKind.FRONT_ANGLE_LEFT, CaptureKind.FRONT_ANGLE_RIGHT),
            session.captures.map { it.kind }.toSet(),
        )
        val expectedFiles = mapOf(
            CaptureKind.FRONT_STRAIGHT to "front-straight.jpg",
            CaptureKind.BACK_STRAIGHT to "back-straight.jpg",
            CaptureKind.FRONT_ANGLE_LEFT to "front-left.jpg",
            CaptureKind.FRONT_ANGLE_RIGHT to "front-right.jpg",
        )
        session.captures.forEach { image ->
            assertEquals("scans/${session.id}/${expectedFiles[image.kind]}", image.localUri)
            val file = File(filesDir, image.localUri)
            assertTrue("${image.kind} file missing", file.isFile && file.length() > 0)
            assertTrue("must be app-private", file.canonicalPath.startsWith(filesDir.canonicalPath))
            assertEquals(3200, image.widthPx)
            assertEquals(2400, image.heightPx)
            assertEquals(6, image.orientation.exifValue)
            assertEquals(2400 to 3200, image.orientedSize)
            assertEquals(Build.MANUFACTURER, image.device.manufacturer)
            assertEquals(Build.MODEL, image.device.model)
            assertTrue(image.device.osVersion.contains("API ${Build.VERSION.SDK_INT}"))
            assertEquals("fake-0", image.device.cameraId)
            assertEquals(100, image.device.iso)
            assertEquals(10_000_000L, image.device.exposureTimeNs)
            assertEquals(image.kind.lighting, image.lighting?.angle)
            assertEquals(QualityVerdict.GOOD, image.quality!!.verdict)
            assertEquals(4, image.quality!!.checks.size)
            assertFalse(image.qualityOverridden)

            // Orientation policy: the oriented loader returns upright (portrait) pixels.
            val bmp = OrientedBitmapLoader.load(file, image.orientation, 800)!!
            assertTrue("upright image must be portrait, was ${bmp.width}x${bmp.height}", bmp.height > bmp.width)
        }
        assertEquals(LightingAngle.LEFT, session.captures.first { it.kind == CaptureKind.FRONT_ANGLE_LEFT }.lighting?.angle)
        assertEquals(4, camera.captureCount)
        assertTrue(File(filesDir, "scans/${session.id}/pending").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun blurryPhotoRequiresOverrideAndOverrideIsRecorded() {
        TestContainer.install(FakeCameraSource(scene = FakeScene.BLURRY))
        rule.startNewScan()
        rule.waitForTag("capture_button")
        rule.clickTag("capture_button")
        rule.waitForTag("quality_summary")
        rule.onNodeWithTag("quality_summary").assertTextContains("Retake recommended", substring = true)
        rule.onNodeWithTag("accept_button").performScrollTo().assertTextContains("Accept anyway")
        rule.clickTag("accept_button")
        rule.waitForText("Accepted (warning overridden)")

        val image = latestSession().captures.single()
        assertTrue(image.qualityOverridden)
        assertEquals(QualityVerdict.RETAKE_RECOMMENDED, image.quality!!.verdict)
    }

    @Test
    fun darkPhotoGetsExposureWarning() {
        TestContainer.install(FakeCameraSource(scene = FakeScene.DARK))
        rule.startNewScan()
        rule.waitForTag("capture_button")
        rule.clickTag("capture_button")
        rule.waitForTag("quality_summary")
        rule.onNodeWithText("Too dark. Add light and retake.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unusablePhotosCannotBeAccepted() {
        val camera = FakeCameraSource(scene = FakeScene.TINY)
        TestContainer.install(camera)
        rule.startNewScan()
        rule.waitForTag("capture_button")
        rule.clickTag("capture_button")
        rule.waitForTag("retake_button")
        rule.onNodeWithTag("quality_summary").assertTextContains("can't be used", substring = true)
        assertTrue(rule.onAllNodesWithTag("accept_button").fetchSemanticsNodes().isEmpty())

        camera.scene = FakeScene.CORRUPT
        rule.clickTag("retake_button")
        rule.waitForTag("capture_button")
        rule.clickTag("capture_button")
        rule.waitForTag("quality_summary")
        rule.onNodeWithText("Readable image").performScrollTo().assertIsDisplayed()
        assertTrue(rule.onAllNodesWithTag("accept_button").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun captureSessionIsRestoredAfterLeavingTheScreen() {
        TestContainer.install(FakeCameraSource())
        rule.startNewScan()
        repeat(2) { rule.captureAndAccept() }
        val sessionId = latestSession().id

        rule.onNodeWithContentDescription("Back").performClick() // capture screen → home (New scan was popped)
        rule.clickText("Saved scans")
        rule.waitForTag("saved_session")
        rule.onAllNodesWithTag("saved_session").onFirst().performClick() // newest session first
        rule.waitForTag("screen_capture")
        rule.waitForText("Step 3 of 4")
        rule.onNodeWithTag("status_FRONT_STRAIGHT", useUnmergedTree = true).assertTextContains("Accepted")
        rule.onNodeWithTag("status_BACK_STRAIGHT", useUnmergedTree = true).assertTextContains("Accepted")
        rule.onNodeWithTag("status_FRONT_ANGLE_LEFT", useUnmergedTree = true).assertTextContains("Not captured")
        assertEquals(sessionId, latestSession().id)
    }

    @Test
    fun sessionsAreIsolated() {
        TestContainer.install(FakeCameraSource())
        rule.startNewScan()
        rule.captureAndAccept()
        val first = latestSession()
        rule.onNodeWithContentDescription("Back").performClick()

        rule.startNewScan()
        rule.captureAndAccept()
        val second = latestSession()

        assertNotEquals(first.id, second.id)
        assertEquals(1, first.captures.size)
        assertEquals(1, runBlocking { container.scanRepository.getSession(second.id)!!.captures.size })
        assertTrue(File(filesDir, "scans/${first.id}/front-straight.jpg").exists())
        assertTrue(File(filesDir, "scans/${second.id}/front-straight.jpg").exists())
        assertNotEquals(first.captures.single().id, second.captures.single().id)
    }
}

/** Delegates to [fake] and records every capture target, which identifies the session the app wrote to. */
private class RecordingCameraSource(private val fake: FakeCameraSource) : CameraSource by fake {
    val targets: MutableList<File> = CopyOnWriteArrayList()

    @Composable
    override fun PreviewContent(modifier: Modifier) = fake.PreviewContent(modifier)

    override suspend fun capture(target: File): CaptureOutcome {
        targets += target
        return fake.capture(target)
    }
}
