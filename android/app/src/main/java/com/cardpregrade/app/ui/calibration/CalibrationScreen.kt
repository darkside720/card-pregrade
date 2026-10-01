package com.cardpregrade.app.ui.calibration

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cardpregrade.app.AppContainer
import com.cardpregrade.app.capture.OrientedBitmapLoader
import com.cardpregrade.app.demo.DemoDiagnostics
import com.cardpregrade.app.ui.components.AppScaffold
import com.cardpregrade.app.ui.components.CardDiagram
import com.cardpregrade.app.ui.components.LabeledValue
import com.cardpregrade.app.ui.components.ScrollingContent
import com.cardpregrade.app.ui.components.SectionTitle
import com.cardpregrade.app.ui.label
import com.cardpregrade.core.cv.diagnostics.DiagnosticArtifact
import com.cardpregrade.core.cv.diagnostics.DiagnosticStage
import com.cardpregrade.core.cv.diagnostics.StageDiagnostics
import com.cardpregrade.core.model.CardSide
import com.cardpregrade.core.model.CapturedImage
import com.cardpregrade.core.model.ScanSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Developer calibration screen. Phase 3 shows real accepted captures and their capture-quality
 * measurements. Every card-analysis stage (boundary, correction, centering, crops, defects) is
 * explicitly NOT IMPLEMENTED — no fabricated output is drawn.
 */
@Composable
fun CalibrationScreen(container: AppContainer, onBack: () -> Unit) {
    var side by rememberSaveable { mutableStateOf(CardSide.FRONT) }
    val diagnostics = DemoDiagnostics.create(side)
    val session by produceState<ScanSession?>(null) {
        value = withContext(Dispatchers.IO) { container.scanRepository.latestCapturedSession() }
    }

    AppScaffold(title = "Developer calibration", screenTag = "screen_calibration", onBack = onBack) { padding ->
        ScrollingContent(padding) {
            Text(
                "Shows real captures and capture-quality measurements. Card analysis stages are not implemented yet.",
                style = MaterialTheme.typography.bodyMedium,
            )
            LabeledValue("Card-analysis pipeline", diagnostics.pipelineVersion)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardSide.entries.forEach { s ->
                    FilterChip(selected = side == s, onClick = { side = s }, label = { Text(s.label()) })
                }
            }

            SectionTitle("Original accepted capture")
            val captures = session?.captures.orEmpty().filter { it.side == side }
            if (captures.isEmpty()) {
                Text(
                    "No accepted ${side.label().lowercase()} captures yet. Complete a guided capture first.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("calibration_no_captures"),
                )
            } else {
                captures.forEach { CaptureDiagnostics(it, container.captureStorage::resolve) }
            }

            SectionTitle("Card-analysis stages")
            diagnostics.stages.filter { it.stage != DiagnosticStage.ORIGINAL }.forEach { StagePanel(it) }
        }
    }
}

@Composable
private fun CaptureDiagnostics(image: CapturedImage, resolveFile: (String) -> File) {
    // Expanded form of keyed produceState, which AGP 8.7 lint misreports as ProduceStateDoesNotAssignValue.
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(image.localUri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { OrientedBitmapLoader.load(resolveFile(image.localUri), image.orientation, 800)?.asImageBitmap() }.getOrNull()
        }
    }
    Card(Modifier.fillMaxWidth().testTag("calibration_capture")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(image.kind.name, style = MaterialTheme.typography.titleSmall)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                bitmap?.let {
                    Image(it, contentDescription = "Accepted capture ${image.kind}", contentScale = ContentScale.Fit, modifier = Modifier.width(200.dp).aspectRatio(3f / 4f))
                } ?: Text("Loading…", style = MaterialTheme.typography.bodySmall)
            }
            val (w, h) = image.orientedSize
            LabeledValue("Upright size", "$w × $h px")
            LabeledValue("Stored size / EXIF", "${image.widthPx} × ${image.heightPx} · orientation ${image.orientation.exifValue ?: "none"} (${image.orientation.rotationDegrees}°)")
            LabeledValue("Camera", image.device.cameraId ?: "not reported")
            LabeledValue("ISO", image.device.iso?.toString() ?: "not reported")
            LabeledValue("Exposure time", image.device.exposureTimeNs?.let { String.format(Locale.ROOT, "%.4f s", it / 1e9) } ?: "not reported")
            LabeledValue("Focal length", image.device.lensFocalLengthMm?.let { String.format(Locale.ROOT, "%.2f mm", it) } ?: "not reported")
            LabeledValue("Quality override", if (image.qualityOverridden) "yes" else "no")
            image.quality?.let { q ->
                Text("Capture-quality measurements (${q.algorithmVersion})", style = MaterialTheme.typography.labelLarge)
                q.checks.forEach { c ->
                    LabeledValue(
                        c.metric.label(),
                        (c.measuredValue?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "—") + " ${c.unit.orEmpty()} · ${c.verdict.label()}",
                    )
                }
            }
        }
    }
}

@Composable
private fun StagePanel(stage: StageDiagnostics) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stage.stage.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                    Text("NOT IMPLEMENTED", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            val regions = stage.artifacts.filterIsInstance<DiagnosticArtifact.Regions>().flatMap { it.regions }
            if (regions.isNotEmpty()) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CardDiagram(modifier = Modifier.width(140.dp), regions = regions)
                }
                stage.artifacts.filterIsInstance<DiagnosticArtifact.Regions>().forEach {
                    Text(it.label, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().height(56.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) { Text("No output", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
            }
            stage.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
