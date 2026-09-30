package com.cardpregrade.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.cardpregrade.app.camera.AndroidCameraPermission
import com.cardpregrade.app.camera.CameraPermission
import com.cardpregrade.app.camera.FakeCameraSource
import com.cardpregrade.app.camera.PermissionStatus
import com.cardpregrade.app.camera.RealCameraSource

/** Simulates the runtime permission without system dialogs (revoking a real permission kills the test process). */
class FakeCameraPermission(var granted: Boolean, private val resultOnRequest: PermissionStatus) : CameraPermission {
    var requests = 0
        private set

    override fun isGranted(context: Context): Boolean = granted

    @Composable
    override fun rememberRequester(onResult: (PermissionStatus) -> Unit): () -> Unit = {
        requests++
        if (resultOnRequest == PermissionStatus.GRANTED) granted = true
        onResult(resultOnRequest)
    }
}

object TestContainer {
    val container: AppContainer
        get() = ApplicationProvider.getApplicationContext<CardPregradeApplication>().container

    fun install(camera: FakeCameraSource, permission: CameraPermission = FakeCameraPermission(true, PermissionStatus.GRANTED)) {
        container.cameraSourceFactory = { camera }
        container.cameraPermission = permission
    }

    fun reset() {
        container.cameraSourceFactory = { RealCameraSource(it) }
        container.cameraPermission = AndroidCameraPermission
    }
}

typealias ComposeRule = AndroidComposeTestRule<*, *>

fun ComposeRule.waitForTag(tag: String, timeoutMs: Long = 20_000) =
    waitUntil(timeoutMs) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

fun ComposeRule.waitForText(text: String, timeoutMs: Long = 20_000) =
    waitUntil(timeoutMs) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

fun ComposeRule.clickTag(tag: String) = onNodeWithTag(tag).performScrollTo().performClick()

fun ComposeRule.clickText(text: String) = onNodeWithText(text).performScrollTo().performClick()

/** Home → New scan → Start guided capture. */
fun ComposeRule.startNewScan() {
    clickText("New scan")
    clickText("Start guided capture")
    waitForTag("screen_capture")
}

/** Capture with the fake camera, wait for review, then accept (with override when needed). */
fun ComposeRule.captureAndAccept() {
    waitForTag("capture_button")
    clickTag("capture_button")
    waitForTag("accept_button")
    clickTag("accept_button")
    waitUntil(20_000) { onAllNodes(hasTestTag("accept_button")).fetchSemanticsNodes().isEmpty() }
}
