package com.cardpregrade.app.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.graphics.Color as ComposeColor

/** What the next capture from [FakeCameraSource] produces. */
enum class FakeScene {
    /** Sharp, well-exposed synthetic card at 3200×2400 → every check GOOD. */
    GOOD,

    /** Featureless frame → sharpness RETAKE_RECOMMENDED (overridable). */
    BLURRY,

    /** Near-black frame → exposure RETAKE_RECOMMENDED (overridable). */
    DARK,

    /** 640×480 → catastrophic resolution, UNUSABLE. */
    TINY,

    /** Bytes that are not an image → decode failure, UNUSABLE. */
    CORRUPT,

    /** takePicture fails. */
    FAIL,
}

/**
 * Debug-only fake camera for instrumented tests and emulator demos. Writes synthetic JPEGs
 * (no copyrighted imagery) the way a phone sensor does: landscape pixels plus an EXIF
 * orientation tag (6 = rotate 90°), so the orientation policy is exercised end to end.
 */
class FakeCameraSource(
    var scene: FakeScene = FakeScene.GOOD,
    available: Boolean = true,
    private val width: Int = 3200,
    private val height: Int = 2400,
    private val exifOrientation: Int = ExifInterface.ORIENTATION_ROTATE_90,
) : CameraSource {

    private val _state = MutableStateFlow<CameraSourceState>(
        if (available) {
            CameraSourceState.Ready(torchSupported = true, exposureRange = -6..6, exposureStepEv = 1f / 3, cameraId = "fake-0")
        } else {
            CameraSourceState.Unavailable("No rear camera was found on this device.")
        },
    )
    override val state: StateFlow<CameraSourceState> = _state.asStateFlow()

    var captureCount = 0
        private set

    @Composable
    override fun PreviewContent(modifier: Modifier) {
        Box(modifier.background(ComposeColor(0xFF263238)).testTag("fake_preview"), contentAlignment = Alignment.BottomCenter) {
            Text("FAKE CAMERA (test)", color = ComposeColor.White)
        }
    }

    override suspend fun capture(target: File): CaptureOutcome {
        val ready = _state.value as? CameraSourceState.Ready ?: return CaptureOutcome.Failure("Camera is not ready.")
        captureCount++
        when (scene) {
            FakeScene.FAIL -> return CaptureOutcome.Failure("Capture failed: simulated camera error")
            FakeScene.CORRUPT -> target.writeBytes(ByteArray(1024) { (it % 251).toByte() })
            FakeScene.TINY -> writeJpeg(target, render(640, 480, sharp = true, dark = false))
            FakeScene.GOOD -> writeJpeg(target, render(width, height, sharp = true, dark = false))
            FakeScene.BLURRY -> writeJpeg(target, render(width, height, sharp = false, dark = false))
            FakeScene.DARK -> writeJpeg(target, render(width, height, sharp = true, dark = true))
        }
        return CaptureOutcome.Success(
            target,
            CaptureCameraInfo(
                cameraId = ready.cameraId,
                torchOn = ready.torchOn,
                exposureCompensationEv = ready.exposureEv,
                configuredWidth = width,
                configuredHeight = height,
                lensFocalLengthMm = null,
            ),
        )
    }

    private fun render(w: Int, h: Int, sharp: Boolean, dark: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val canvas = Canvas(bmp)
        if (dark) {
            canvas.drawColor(Color.rgb(8, 8, 8))
            return bmp
        }
        canvas.drawColor(Color.rgb(96, 96, 96))
        if (!sharp) return bmp
        // Synthetic "card" (landscape sensor orientation) with a fine texture for sharpness.
        val paint = Paint()
        val cardW = h * 0.7f
        val cardH = cardW / (63f / 88f)
        val left = (w - cardH) / 2
        val top = (h - cardW) / 2
        paint.color = Color.rgb(230, 200, 80)
        canvas.drawRect(left, top, left + cardH, top + cardW, paint)
        paint.color = Color.rgb(60, 110, 176)
        val inset = cardW * 0.06f
        canvas.drawRect(left + inset, top + inset, left + cardH - inset, top + cardW - inset, paint)
        paint.color = Color.rgb(170, 190, 220)
        val step = w / 160f
        var x = left + inset
        while (x < left + cardH - inset) {
            canvas.drawRect(x, top + inset, x + step / 2, top + cardW - inset, paint)
            x += step
        }
        return bmp
    }

    private fun writeJpeg(target: File, bmp: Bitmap) {
        FileOutputStream(target).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        ExifInterface(target).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation.toString())
            setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "100")
            setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "0.01")
            saveAttributes()
        }
    }

    override fun focusAt(x: Float, y: Float) = Unit

    override fun setTorch(enabled: Boolean) {
        _state.update { (it as? CameraSourceState.Ready)?.copy(torchOn = enabled) ?: it }
    }

    override fun setExposureCompensationIndex(index: Int) {
        _state.update { (it as? CameraSourceState.Ready)?.copy(exposureIndex = index) ?: it }
    }

    override fun release() = Unit
}
