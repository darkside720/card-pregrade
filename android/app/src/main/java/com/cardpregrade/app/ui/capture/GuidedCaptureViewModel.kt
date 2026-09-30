package com.cardpregrade.app.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardpregrade.app.camera.CameraSource
import com.cardpregrade.app.camera.CaptureOutcome
import com.cardpregrade.app.capture.CaptureInspector
import com.cardpregrade.app.capture.CaptureStorage
import com.cardpregrade.app.capture.DeviceInfo
import com.cardpregrade.core.data.repository.ScanRepository
import com.cardpregrade.core.model.CaptureKind
import com.cardpregrade.core.model.CaptureProtocol
import com.cardpregrade.core.model.CapturedImage
import com.cardpregrade.core.model.DeviceMetadata
import com.cardpregrade.core.model.LightingMetadata
import com.cardpregrade.core.model.ScanStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID

data class CaptureUiState(
    /** Null while the session is being restored from storage. */
    val flow: CaptureFlowState? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * Orchestrates camera → file → quality check → review → accept/retake → persistence.
 * Accepted photos are persisted immediately, so an interrupted session resumes where it left off.
 */
class GuidedCaptureViewModel(
    private val sessionId: String,
    private val scans: ScanRepository,
    private val storage: CaptureStorage,
    private val inspector: CaptureInspector,
    val camera: CameraSource,
    private val device: DeviceInfo,
    private val clock: Clock = Clock.systemUTC(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    private val protocol: CaptureProtocol = CaptureProtocol.V1_STANDARD,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val accepted = withContext(io) {
                storage.clearPending(sessionId)
                scans.getSession(sessionId)?.captures.orEmpty()
            }
            _state.update { it.copy(flow = CaptureFlowState.restore(protocol, accepted)) }
        }
    }

    fun capture() {
        val flow = _state.value.flow ?: return
        if (_state.value.busy || flow.current.status != StepStatus.PENDING) return
        val kind = flow.current.step.kind
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val file = withContext(io) { storage.newPendingFile(sessionId, kind) }
            when (val outcome = camera.capture(file)) {
                is CaptureOutcome.Failure -> {
                    withContext(io) { storage.discard(file) }
                    _state.update { it.copy(busy = false, error = outcome.message) }
                }
                is CaptureOutcome.Success -> {
                    val imageId = UUID.randomUUID().toString()
                    val inspected = withContext(compute) { inspector.inspect(imageId, outcome.file) }
                    val image = CapturedImage(
                        id = imageId,
                        sessionId = sessionId,
                        kind = kind,
                        localUri = storage.relativePath(outcome.file),
                        widthPx = inspected.storedWidth,
                        heightPx = inspected.storedHeight,
                        capturedAt = clock.instant(),
                        device = DeviceMetadata(
                            manufacturer = device.manufacturer,
                            model = device.model,
                            osVersion = device.osVersion,
                            appVersion = device.appVersion,
                            cameraId = outcome.info.cameraId,
                            lensFocalLengthMm = inspected.focalLengthMm ?: outcome.info.lensFocalLengthMm,
                            iso = inspected.iso,
                            exposureTimeNs = inspected.exposureTimeNs,
                            flashUsed = inspected.flashFired,
                            exposureCompensationEv = outcome.info.exposureCompensationEv,
                            torchOn = outcome.info.torchOn,
                        ),
                        lighting = LightingMetadata(kind.lighting),
                        quality = inspected.quality,
                        orientation = inspected.orientation,
                    )
                    _state.update { s -> s.copy(busy = false, flow = s.flow?.onCaptured(image)) }
                }
            }
        }
    }

    /** Accepts the reviewed photo. [override] is required when the quality verdict is not GOOD. */
    fun accept(override: Boolean) {
        val flow = _state.value.flow ?: return
        val toStore = flow.pendingAcceptance(override) ?: return
        val kind = toStore.kind
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val stored = withContext(io) {
                    val finalFile = storage.promote(storage.resolve(toStore.localUri), sessionId, kind)
                    val image = toStore.copy(localUri = storage.relativePath(finalFile))
                    scans.addCapture(image)
                    image
                }
                _state.update { s ->
                    val next = s.flow?.accept(override)
                    val withStoredPath = next?.let { n ->
                        n.copy(captures = n.captures.map { c -> if (c.step.kind == kind) c.copy(accepted = stored) else c })
                    }
                    s.copy(busy = false, flow = withStoredPath)
                }
                withContext(io) {
                    val complete = _state.value.flow?.isComplete == true
                    scans.updateStatus(sessionId, if (complete) ScanStatus.CAPTURED else ScanStatus.CAPTURING)
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "Could not save the photo: ${e.message}") }
            }
        }
    }

    fun retake(kind: CaptureKind) {
        val flow = _state.value.flow ?: return
        val step = flow.captures.first { it.step.kind == kind }
        _state.update { it.copy(flow = flow.retake(kind), error = null) }
        viewModelScope.launch(io) {
            step.candidate?.let { storage.discard(storage.resolve(it.localUri)) }
            if (step.accepted != null) {
                scans.removeCapture(sessionId, kind)
                storage.deleteAccepted(sessionId, kind)
                scans.updateStatus(sessionId, ScanStatus.CAPTURING)
            }
        }
    }

    fun select(kind: CaptureKind) = _state.update { s -> s.copy(flow = s.flow?.select(kind)) }

    fun dismissError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        camera.release()
    }
}
