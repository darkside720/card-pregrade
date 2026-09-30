package com.cardpregrade.app.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Camera boundary used by the guided-capture UI. The UI and ViewModel depend only on this
 * interface; CameraX lives in [RealCameraSource]. A fake implementation (debug source set)
 * drives instrumented tests without camera hardware.
 */
interface CameraSource {
    val state: StateFlow<CameraSourceState>

    /** Live preview. The capture guide is drawn by the caller on top, so it never enters the photo. */
    @Composable
    fun PreviewContent(modifier: Modifier)

    /**
     * Captures a full-resolution JPEG into [target]. Never silently retries at a lower quality:
     * any failure is returned as [CaptureOutcome.Failure].
     */
    suspend fun capture(target: File): CaptureOutcome

    /** Tap-to-focus at a point in preview coordinates (pixels within the preview view). */
    fun focusAt(x: Float, y: Float)

    fun setTorch(enabled: Boolean)

    fun setExposureCompensationIndex(index: Int)

    /** Releases the camera. Safe to call more than once. */
    fun release()
}

sealed interface CameraSourceState {
    data object Initializing : CameraSourceState

    data class Ready(
        val torchSupported: Boolean,
        val torchOn: Boolean = false,
        /** Null when exposure compensation is not supported. */
        val exposureRange: IntRange? = null,
        val exposureIndex: Int = 0,
        /** EV per index step (e.g. 1/3). */
        val exposureStepEv: Float = 0f,
        val cameraId: String? = null,
    ) : CameraSourceState {
        val exposureEv: Float get() = exposureIndex * exposureStepEv
    }

    /** No usable rear camera (e.g. device without one). */
    data class Unavailable(val reason: String) : CameraSourceState

    data class Error(val message: String) : CameraSourceState
}

/** Camera-side facts about one capture. Per-shot EXIF values are read from the file afterwards. */
data class CaptureCameraInfo(
    val cameraId: String?,
    val torchOn: Boolean,
    val exposureCompensationEv: Float?,
    /** Resolution CameraX configured for ImageCapture, if known (the file's real size is authoritative). */
    val configuredWidth: Int?,
    val configuredHeight: Int?,
    /** Lens focal length from camera characteristics when the device reports exactly one. */
    val lensFocalLengthMm: Float?,
)

sealed interface CaptureOutcome {
    data class Success(val file: File, val info: CaptureCameraInfo) : CaptureOutcome
    data class Failure(val message: String) : CaptureOutcome
}
