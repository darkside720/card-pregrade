package com.cardpregrade.app.ui.calibration

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardpregrade.app.capture.CaptureBoundaryResult
import com.cardpregrade.app.ui.components.LabeledValue
import com.cardpregrade.core.cv.detection.BoundaryDetection
import com.cardpregrade.core.cv.detection.CardDetectionPolicy
import com.cardpregrade.core.cv.detection.Side
import com.cardpregrade.core.cv.geometry.PixelPoint

/** Per-capture state of the developer boundary diagnostics. */
sealed interface CaptureBoundaryState {
    data object Loading : CaptureBoundaryState

    /** The capture itself could not be loaded (not a detection result). */
    data class LoadFailed(val reason: String) : CaptureBoundaryState

    /**
     * Detection finished. [bitmap] is the exact working bitmap the detector's grey image was made
     * from; the grey array itself is not retained.
     */
    class Ready(val bitmap: Bitmap, val policy: CardDetectionPolicy, val detection: BoundaryDetection) : CaptureBoundaryState {
        constructor(result: CaptureBoundaryResult) : this(result.image.bitmap, result.policy, result.detection)
    }
}

/**
 * The working bitmap with the detector's result drawn over it, followed by the diagnostics.
 * Pure display: detection already ran (see CalibrationScreen); nothing here starts work.
 */
@Composable
fun CaptureBoundaryPanel(state: CaptureBoundaryState, contentDescription: String) {
    when (state) {
        CaptureBoundaryState.Loading -> Text("Loading and detecting…", style = MaterialTheme.typography.bodySmall)
        is CaptureBoundaryState.LoadFailed -> Text(
            "Capture could not be loaded: ${state.reason}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        is CaptureBoundaryState.Ready -> ReadyPanel(state, contentDescription)
    }
}

@Composable
private fun ReadyPanel(state: CaptureBoundaryState.Ready, contentDescription: String) {
    val bitmap = state.bitmap
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val presentation = remember(state) {
        DetectionPresenter.present(bitmap.width, bitmap.height, state.policy, state.detection)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            presentation.headline,
            style = MaterialTheme.typography.titleSmall,
            color = if (presentation.detected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("boundary_headline"),
        )
        // One box for both layers so the Canvas has exactly the Image's bounds; clipped so evidence
        // outside the image (e.g. a rejected quad's far corner) cannot paint over other rows. The
        // ratio is clamped so an extreme bitmap cannot demand an unrepresentable height inside the
        // scrolling column; FitMapping reproduces the letterbox that clamping can introduce.
        val ratio = (bitmap.width.toFloat() / bitmap.height).coerceIn(MIN_ASPECT, MAX_ASPECT)
        Box(Modifier.fillMaxWidth().aspectRatio(ratio).clipToBounds()) {
            Image(imageBitmap, contentDescription, contentScale = ContentScale.Fit, modifier = Modifier.matchParentSize())
            BoundaryCanvas(presentation.shapes, bitmap.width, bitmap.height, Modifier.matchParentSize().testTag("boundary_overlay"))
        }
        presentation.failureRows.forEach { LabeledValue(it.label, it.value) }
        presentation.imageRows.forEach { LabeledValue(it.label, it.value) }
        presentation.sides.forEach { side ->
            Text("${side.side.name} side", style = MaterialTheme.typography.labelLarge, color = sideColor(side.side))
            side.rows.forEach { LabeledValue(it.label, it.value) }
        }
        Text("Detector policy", style = MaterialTheme.typography.labelLarge)
        presentation.policyRows.forEach { LabeledValue(it.label, it.value) }
        Text(DetectionPresenter.KNOWN_LIMITATIONS, style = MaterialTheme.typography.bodySmall)
    }
}

private const val MIN_ASPECT = 0.2f
private const val MAX_ASPECT = 5f

private val DETECTED_COLOR = Color(0xFF00E676)
private val REJECTED_COLOR = Color(0xFFFF5252)

private fun sideColor(side: Side): Color = when (side) {
    Side.LEFT -> Color(0xFF40C4FF)
    Side.TOP -> Color(0xFFE040FB)
    Side.RIGHT -> Color(0xFFFFD740)
    Side.BOTTOM -> Color(0xFFFF9100)
}

private val CORNER_LABELS = listOf("TL", "TR", "BR", "BL")

@Composable
private fun BoundaryCanvas(shapes: OverlayShapes, imageWidth: Int, imageHeight: Int, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    // Labels are laid out once, unconstrained, and only positioned while drawing: drawing a
    // pre-measured layout cannot fail on a position near or past the canvas edge.
    val labels = remember(measurer, shapes) {
        val texts = shapes.sideSegments.keys.map { it.name to sideColor(it) } +
            CORNER_LABELS.map { it to if (shapes.quadRejected) REJECTED_COLOR else DETECTED_COLOR }
        texts.associate { (text, color) ->
            text to measurer.measure(text, TextStyle(color = color, fontSize = 11.sp, background = Color.Black.copy(alpha = 0.6f)))
        }
    }
    Canvas(modifier) {
        val m = FitMapping.of(imageWidth, imageHeight, size.width, size.height)
        fun view(p: PixelPoint) = Offset(m.toViewX(p.x).toFloat(), m.toViewY(p.y).toFloat())

        shapes.sideSegments.forEach { (side, segment) ->
            val a = view(segment.first)
            val b = view(segment.second)
            drawLine(sideColor(side).copy(alpha = 0.7f), a, b, strokeWidth = 1.dp.toPx())
            label(labels.getValue(side.name), Offset((a.x + b.x) / 2, (a.y + b.y) / 2))
        }
        shapes.inliers.forEach { (side, points) ->
            points.forEach { drawCircle(sideColor(side), radius = 2.5.dp.toPx(), center = view(it)) }
        }
        shapes.quad?.let { quad ->
            val corners = quad.points.map(::view)
            // A corner far outside the image can exceed Float range; there is nothing to draw then.
            if (corners.any { !it.x.isFinite() || !it.y.isFinite() }) return@let
            val color = if (shapes.quadRejected) REJECTED_COLOR else DETECTED_COLOR
            val outline = Path().apply {
                moveTo(corners[0].x, corners[0].y)
                corners.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            val dash = if (shapes.quadRejected) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null
            drawPath(outline, color, style = Stroke(width = 2.dp.toPx(), pathEffect = dash))
            corners.forEachIndexed { i, c ->
                drawCircle(color, radius = 5.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
                label(labels.getValue(CORNER_LABELS[i]), c)
            }
        }
    }
}

/** Draws [layout] just below-right of [at], kept inside the canvas so the label stays readable. */
private fun DrawScope.label(layout: TextLayoutResult, at: Offset) {
    val maxX = (size.width - layout.size.width).coerceAtLeast(0f)
    val maxY = (size.height - layout.size.height).coerceAtLeast(0f)
    val x = (at.x + 4.dp.toPx()).coerceIn(0f, maxX)
    val y = (at.y + 4.dp.toPx()).coerceIn(0f, maxY)
    drawText(layout, topLeft = Offset(x, y))
}
