package com.cardpregrade.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraX implementation: rear camera, 4:3 Preview + full-resolution ImageCapture
 * (CAPTURE_MODE_MAXIMIZE_QUALITY, highest available resolution), bound to the composable's
 * lifecycle. The preview uses FIT_CENTER so the on-screen frame shows the same 4:3 field of
 * view the photo will contain.
 */
class RealCameraSource(context: Context) : CameraSource {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<CameraSourceState>(CameraSourceState.Initializing)
    override val state: StateFlow<CameraSourceState> = _state.asStateFlow()

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var previewView: PreviewView? = null
    private var singleFocalLengthMm: Float? = null

    @Composable
    override fun PreviewContent(modifier: Modifier) {
        val lifecycleOwner = LocalLifecycleOwner.current
        val view = remember {
            PreviewView(appContext).apply {
                scaleType = PreviewView.ScaleType.FIT_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        }
        AndroidView(factory = { view }, modifier = modifier)
        LaunchedEffect(lifecycleOwner, view) { bind(lifecycleOwner, view) }
        DisposableEffect(view) {
            onDispose { release() }
        }
    }

    private suspend fun bind(owner: LifecycleOwner, view: PreviewView) {
        _state.value = CameraSourceState.Initializing
        try {
            val cameraProvider = awaitProvider()
            provider = cameraProvider
            if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                _state.value = CameraSourceState.Unavailable("No rear camera was found on this device.")
                return
            }
            val selector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build()
            val preview = Preview.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build(),
                )
                .build()
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setResolutionSelector(selector)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()
            cameraProvider.unbindAll()
            val bound = cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            preview.setSurfaceProvider(view.surfaceProvider)
            camera = bound
            imageCapture = capture
            previewView = view

            val exposure = bound.cameraInfo.exposureState
            val (cameraId, focal) = cameraIdentity(bound)
            singleFocalLengthMm = focal
            _state.value = CameraSourceState.Ready(
                torchSupported = bound.cameraInfo.hasFlashUnit(),
                exposureRange = if (exposure.isExposureCompensationSupported) {
                    exposure.exposureCompensationRange.lower..exposure.exposureCompensationRange.upper
                } else {
                    null
                },
                exposureIndex = exposure.exposureCompensationIndex,
                exposureStepEv = exposure.exposureCompensationStep.toFloat(),
                cameraId = cameraId,
            )
        } catch (e: Exception) {
            _state.value = CameraSourceState.Error("Camera could not be started: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    @OptIn(markerClass = [ExperimentalCamera2Interop::class])
    private fun cameraIdentity(camera: Camera): Pair<String?, Float?> = runCatching {
        val info = Camera2CameraInfo.from(camera.cameraInfo)
        val focalLengths = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        info.cameraId to focalLengths?.singleOrNull()
    }.getOrDefault(null to null)

    private suspend fun awaitProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    override suspend fun capture(target: File): CaptureOutcome {
        val capture = imageCapture ?: return CaptureOutcome.Failure("Camera is not ready.")
        val ready = _state.value as? CameraSourceState.Ready
            ?: return CaptureOutcome.Failure("Camera is not ready.")
        capture.targetRotation = previewView?.display?.rotation ?: Surface.ROTATION_0
        val options = ImageCapture.OutputFileOptions.Builder(target).build()
        return suspendCancellableCoroutine { cont ->
            capture.takePicture(
                options,
                ContextCompat.getMainExecutor(appContext),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val resolution = capture.resolutionInfo?.resolution
                        cont.resume(
                            CaptureOutcome.Success(
                                file = target,
                                info = CaptureCameraInfo(
                                    cameraId = ready.cameraId,
                                    torchOn = ready.torchOn,
                                    exposureCompensationEv = if (ready.exposureRange != null) ready.exposureEv else null,
                                    configuredWidth = resolution?.width,
                                    configuredHeight = resolution?.height,
                                    lensFocalLengthMm = singleFocalLengthMm,
                                ),
                            ),
                        )
                    }

                    override fun onError(exception: ImageCaptureException) {
                        target.delete()
                        cont.resume(CaptureOutcome.Failure("Capture failed: ${exception.message ?: "error ${exception.imageCaptureError}"}"))
                    }
                },
            )
        }
    }

    override fun focusAt(x: Float, y: Float) {
        val cam = camera ?: return
        val view = previewView ?: return
        val point = view.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point).setAutoCancelDuration(5, TimeUnit.SECONDS).build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    override fun setTorch(enabled: Boolean) {
        val cam = camera ?: return
        if (!cam.cameraInfo.hasFlashUnit()) return
        cam.cameraControl.enableTorch(enabled)
        _state.update { (it as? CameraSourceState.Ready)?.copy(torchOn = enabled) ?: it }
    }

    override fun setExposureCompensationIndex(index: Int) {
        val cam = camera ?: return
        val ready = _state.value as? CameraSourceState.Ready ?: return
        val range = ready.exposureRange ?: return
        val clamped = index.coerceIn(range)
        cam.cameraControl.setExposureCompensationIndex(clamped)
        _state.value = ready.copy(exposureIndex = clamped)
    }

    override fun release() {
        provider?.unbindAll()
        camera = null
        imageCapture = null
        previewView = null
    }
}
