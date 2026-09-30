package com.cardpregrade.app.ui.capture

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardpregrade.app.AppContainer
import com.cardpregrade.app.camera.CameraSource
import com.cardpregrade.app.camera.CameraSourceState
import com.cardpregrade.app.camera.PermissionStatus
import com.cardpregrade.app.capture.DeviceInfo
import com.cardpregrade.app.capture.OrientedBitmapLoader
import com.cardpregrade.app.ui.components.AppScaffold
import com.cardpregrade.app.ui.components.CardGuideOverlay
import com.cardpregrade.app.ui.components.ScrollingContent
import com.cardpregrade.app.ui.label
import com.cardpregrade.core.model.CaptureDirection
import com.cardpregrade.core.model.CapturedImage
import com.cardpregrade.core.model.QualityVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun GuidedCaptureScreen(container: AppContainer, sessionId: String, onBack: () -> Unit, onContinue: () -> Unit) {
    val context = LocalContext.current
    val vm: GuidedCaptureViewModel = viewModel {
        GuidedCaptureViewModel(
            sessionId = sessionId,
            scans = container.scanRepository,
            storage = container.captureStorage,
            inspector = container.captureInspector,
            camera = container.cameraSourceFactory(context.applicationContext),
            device = DeviceInfo.current(),
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val permission = container.cameraPermission
    var permissionStatus by rememberSaveable {
        mutableStateOf(if (permission.isGranted(context)) PermissionStatus.GRANTED else PermissionStatus.NOT_REQUESTED)
    }
    val requestPermission = permission.rememberRequester { permissionStatus = it }
    // Returning from system settings may have granted the permission.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (permission.isGranted(context)) permissionStatus = PermissionStatus.GRANTED
    }

    AppScaffold(title = "Guided capture", screenTag = "screen_capture", onBack = onBack) { padding ->
        ScrollingContent(padding) {
            val flow = state.flow
            if (flow == null) {
                CircularProgressIndicator()
                return@ScrollingContent
            }
            val total = flow.captures.size
            LinearProgressIndicator(progress = { flow.capturedCount / total.toFloat() }, modifier = Modifier.fillMaxWidth())
            Text("Step ${flow.currentIndex + 1} of $total — ${flow.current.step.title}", style = MaterialTheme.typography.titleMedium)
            DirectionHint(flow.current.step.direction)
            Text(flow.current.step.instructions, style = MaterialTheme.typography.bodyMedium)

            val current = flow.current
            when {
                current.status == StepStatus.PENDING && permissionStatus != PermissionStatus.GRANTED ->
                    PermissionPanel(permissionStatus, onRequest = requestPermission)
                current.status == StepStatus.PENDING ->
                    CameraPanel(vm.camera, busy = state.busy, onCapture = vm::capture)
                else -> current.shown?.let { image ->
                    ReviewPanel(
                        image = image,
                        status = current.status,
                        resolveFile = container.captureStorage::resolve,
                        busy = state.busy,
                        onAccept = vm::accept,
                        onRetake = { vm.retake(current.step.kind) },
                    )
                }
            }

            state.error?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().testTag("capture_error")) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = vm::dismissError) { Text("Dismiss") }
                    }
                }
            }

            Text("Capture checklist", style = MaterialTheme.typography.titleSmall)
            flow.captures.forEachIndexed { index, capture ->
                ChecklistRow(index, capture, selected = index == flow.currentIndex, onClick = { vm.select(capture.step.kind) })
            }

            Button(onClick = onContinue, enabled = flow.isComplete && !state.busy, modifier = Modifier.fillMaxWidth().testTag("continue_button")) {
                Text("Continue to analysis")
            }
            Text(
                "Session ${sessionId.take(8)} · Photos are stored only in this app's private storage on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DirectionHint(direction: CaptureDirection) {
    val (icon, text) = when (direction) {
        CaptureDirection.NONE -> return
        CaptureDirection.ROTATE_LEFT -> Icons.AutoMirrored.Filled.KeyboardArrowLeft to "Rotate card slightly left"
        CaptureDirection.ROTATE_RIGHT -> Icons.AutoMirrored.Filled.KeyboardArrowRight to "Rotate card slightly right"
    }
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun PermissionPanel(status: PermissionStatus, onRequest: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().testTag("permission_panel")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Camera access needed", style = MaterialTheme.typography.titleMedium)
            Text(
                "Card Pre-Grade uses the camera only to photograph your card. Photos stay in this app's private " +
                    "storage on this device and are never uploaded.",
                style = MaterialTheme.typography.bodyMedium,
            )
            when (status) {
                PermissionStatus.PERMANENTLY_DENIED -> {
                    Text(
                        "Camera permission was denied. Enable it in the app's system settings to continue.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("permission_denied"),
                    )
                    Button(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text("Open app settings") }
                }
                PermissionStatus.DENIED -> {
                    Text(
                        "Camera permission was denied. Photos cannot be taken without it.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("permission_denied"),
                    )
                    Button(onClick = onRequest, modifier = Modifier.testTag("permission_request")) { Text("Allow camera") }
                }
                else -> Button(onClick = onRequest, modifier = Modifier.testTag("permission_request")) { Text("Allow camera") }
            }
        }
    }
}

@Composable
private fun CameraPanel(camera: CameraSource, busy: Boolean, onCapture: () -> Unit) {
    val cameraState by camera.state.collectAsStateWithLifecycle()
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(1200)
            focusPoint = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .background(Color.Black, MaterialTheme.shapes.medium)
            .testTag("camera_preview"),
        contentAlignment = Alignment.Center,
    ) {
        camera.PreviewContent(Modifier.fillMaxSize())
        CardGuideOverlay(
            Modifier
                .fillMaxSize()
                .pointerInput(camera) {
                    detectTapGestures { offset ->
                        focusPoint = offset
                        camera.focusAt(offset.x, offset.y)
                    }
                },
        )
        focusPoint?.let { FocusRing(it) }
        when (val s = cameraState) {
            CameraSourceState.Initializing -> CircularProgressIndicator(color = Color.White)
            is CameraSourceState.Unavailable -> CameraMessage(s.reason, "camera_unavailable")
            is CameraSourceState.Error -> CameraMessage(s.message, "camera_error")
            is CameraSourceState.Ready -> Unit
        }
        if (busy) CircularProgressIndicator(color = Color.White)
    }
    Text(
        "Place the card inside the frame. Tap to focus. The frame is a guide only — it is not added to the photo.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val ready = cameraState as? CameraSourceState.Ready
    if (ready != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ready.torchSupported) {
                FilterChip(selected = ready.torchOn, onClick = { camera.setTorch(!ready.torchOn) }, label = { Text(if (ready.torchOn) "Torch on" else "Torch off") })
            }
            ready.exposureRange?.let { range ->
                Text(String.format(Locale.ROOT, "Exposure %+.1f EV", ready.exposureEv), style = MaterialTheme.typography.labelMedium)
            }
        }
        ready.exposureRange?.takeIf { it.first < it.last }?.let { range ->
            Slider(
                value = ready.exposureIndex.toFloat(),
                onValueChange = { camera.setExposureCompensationIndex(it.roundToInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first - 1).coerceAtLeast(0),
                modifier = Modifier.testTag("exposure_slider"),
            )
        }
    }
    Button(
        onClick = onCapture,
        enabled = ready != null && !busy,
        modifier = Modifier.fillMaxWidth().testTag("capture_button"),
    ) { Text(if (busy) "Capturing…" else "Capture") }
}

@Composable
private fun BoxScope.FocusRing(at: Offset) {
    val sizeDp = 56.dp
    val half = with(LocalDensity.current) { (sizeDp / 2).toPx() }
    Box(
        Modifier
            .offset { IntOffset((at.x - half).roundToInt(), (at.y - half).roundToInt()) }
            .size(sizeDp)
            .border(2.dp, Color.White, CircleShape)
            .align(Alignment.TopStart),
    )
}

@Composable
private fun CameraMessage(text: String, tag: String) {
    Surface(color = Color.Black.copy(alpha = 0.7f), shape = MaterialTheme.shapes.small, modifier = Modifier.padding(24.dp).testTag(tag)) {
        Text(text, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun ReviewPanel(
    image: CapturedImage,
    status: StepStatus,
    resolveFile: (String) -> File,
    busy: Boolean,
    onAccept: (override: Boolean) -> Unit,
    onRetake: () -> Unit,
) {
    val bitmap by produceState<ImageBitmap?>(null, image.localUri) {
        value = withContext(Dispatchers.IO) {
            runCatching { OrientedBitmapLoader.load(resolveFile(image.localUri), image.orientation, 1600)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .background(Color.Black, MaterialTheme.shapes.medium)
            .testTag("review_image"),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { Image(it, contentDescription = "Captured photo", modifier = Modifier.fillMaxSize()) }
            ?: Text("Loading photo…", color = Color.White)
    }
    val (w, h) = image.orientedSize
    Text(
        "Captured ${w} × ${h} px (upright) · stored ${image.widthPx} × ${image.heightPx}, rotation ${image.orientation.rotationDegrees}°",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag("capture_resolution"),
    )
    QualityPanel(image)

    when (status) {
        StepStatus.REVIEWING -> {
            val verdict = image.quality?.verdict ?: QualityVerdict.WARNING
            val good = verdict == QualityVerdict.GOOD
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRetake, enabled = !busy, modifier = Modifier.weight(1f).testTag("retake_button")) { Text("Retake") }
                Button(onClick = { onAccept(!good) }, enabled = !busy, modifier = Modifier.weight(1f).testTag("accept_button")) {
                    Text(if (good) "Accept" else "Accept anyway")
                }
            }
        }
        StepStatus.UNUSABLE -> Button(onClick = onRetake, modifier = Modifier.fillMaxWidth().testTag("retake_button")) { Text("Retake") }
        StepStatus.CAPTURED -> {
            Text(
                if (image.qualityOverridden) "Accepted (quality warning overridden)" else "Accepted",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.testTag("accepted_label"),
            )
            OutlinedButton(onClick = onRetake, modifier = Modifier.fillMaxWidth().testTag("retake_button")) { Text("Retake this photo") }
        }
        StepStatus.PENDING -> Unit
    }
}

@Composable
private fun QualityPanel(image: CapturedImage) {
    val quality = image.quality ?: return
    val verdict = quality.verdict
    OutlinedCard(Modifier.fillMaxWidth().testTag("quality_panel")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Image quality", style = MaterialTheme.typography.titleSmall)
            Text(
                "Capture check only — this is not a card grade. Thresholds are provisional.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            quality.checks.forEach { check ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(check.metric.label(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    VerdictChip(check.verdict)
                }
                if (check.verdict != QualityVerdict.GOOD) {
                    Text(check.message, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                when (verdict) {
                    QualityVerdict.GOOD -> "Photo looks usable."
                    QualityVerdict.WARNING -> "Capture warning. You can retake or accept anyway."
                    QualityVerdict.RETAKE_RECOMMENDED -> "Retake recommended. You can still accept anyway."
                    QualityVerdict.UNUSABLE -> "This photo can't be used. Please retake."
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("quality_summary"),
            )
        }
    }
}

@Composable
private fun VerdictChip(verdict: QualityVerdict) {
    val color = when (verdict) {
        QualityVerdict.GOOD -> Color(0xFF2E7D32)
        QualityVerdict.WARNING -> Color(0xFFF9A825)
        QualityVerdict.RETAKE_RECOMMENDED -> Color(0xFFEF6C00)
        QualityVerdict.UNUSABLE -> Color(0xFFC62828)
    }
    Surface(color = color.copy(alpha = 0.15f), contentColor = color, shape = MaterialTheme.shapes.small) {
        Text(verdict.label(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun ChecklistRow(index: Int, capture: StepCapture, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f) else Color.Transparent)
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .testTag("checklist_${capture.step.kind.name}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val (icon, tint) = when (capture.status) {
            StepStatus.CAPTURED -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
            StepStatus.UNUSABLE -> Icons.Outlined.Warning to MaterialTheme.colorScheme.error
            else -> Icons.Outlined.Info to MaterialTheme.colorScheme.outline
        }
        Icon(icon, contentDescription = null, tint = tint)
        Column(Modifier.weight(1f)) {
            Text("${index + 1}. ${capture.step.title}", style = MaterialTheme.typography.bodyMedium)
            Text(
                when (capture.status) {
                    StepStatus.PENDING -> "Not captured"
                    StepStatus.REVIEWING -> "Awaiting review"
                    StepStatus.UNUSABLE -> "Unusable — retake required"
                    StepStatus.CAPTURED -> if (capture.accepted?.qualityOverridden == true) "Accepted (warning overridden)" else "Accepted"
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("status_${capture.step.kind.name}"),
            )
        }
    }
}
