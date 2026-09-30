package com.cardpregrade.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cardpregrade.core.cv.geometry.CardGeometry
import com.cardpregrade.core.model.CardDefect
import com.cardpregrade.core.model.DefectSeverity
import com.cardpregrade.core.model.DetectionMaturity
import com.cardpregrade.core.model.NormalizedRect

private val CardBody = Color(0xFF37474F)
private val CardArt = Color(0xFF546E7A)
private val CardArtLines = Color(0xFF78909C)
private val RegionColor = Color(0xFF4FC3F7)
private val GuideColor = Color(0xFFB2FF59)

fun severityColor(severity: DefectSeverity): Color = when (severity) {
    DefectSeverity.MAJOR -> Color(0xFFE53935)
    DefectSeverity.MODERATE -> Color(0xFFFF7043)
    DefectSeverity.MINOR -> Color(0xFFFFB300)
    DefectSeverity.TRACE -> Color(0xFFFFEE58)
}

/** Normalized inset of the illustrative art box (not a measurement). */
private const val ART_INSET = 0.07

/** Markers are drawn at least this large (fraction of card width) so thin edge defects stay tappable. */
private const val MIN_MARKER = 0.07

private data class DiagramTransform(val cardSize: Size, val scale: Float, val translate: Offset) {
    fun toCard(point: Offset): Offset = Offset((point.x - translate.x) / scale, (point.y - translate.y) / scale)
}

private fun transformFor(canvas: Size, focus: NormalizedRect?): DiagramTransform {
    val aspect = CardGeometry.STANDARD_ASPECT_RATIO.toFloat()
    if (focus == null) return DiagramTransform(canvas, 1f, Offset.Zero)
    val card = Size(canvas.width, canvas.width / aspect)
    val pad = 0.08f * card.width
    val left = (focus.left.toFloat() * card.width - pad)
    val right = (focus.right.toFloat() * card.width + pad)
    val top = (focus.top.toFloat() * card.height - pad)
    val bottom = (focus.bottom.toFloat() * card.height + pad)
    val scale = minOf(canvas.width / (right - left), canvas.height / (bottom - top)).coerceIn(1f, 10f)
    val translate = Offset(canvas.width / 2 - scale * (left + right) / 2, canvas.height / 2 - scale * (top + bottom) / 2)
    return DiagramTransform(card, scale, translate)
}

private fun markerRect(box: NormalizedRect, card: Size): Rect {
    val minW = (MIN_MARKER * card.width).toFloat()
    val cx = box.centerX.toFloat() * card.width
    val cy = box.centerY.toFloat() * card.height
    val w = maxOf(box.width.toFloat() * card.width, minW)
    val h = maxOf(box.height.toFloat() * card.height, minW)
    return Rect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
}

/**
 * Schematic card (an illustration, not a photo) with defect markers and optional analysis
 * regions. When [focus] is set, the view zooms to that region — used for the tap-to-zoom
 * defect detail. Phase 3+ will draw the corrected photograph underneath the same overlay.
 */
@Composable
fun CardDiagram(
    modifier: Modifier = Modifier,
    defects: List<CardDefect> = emptyList(),
    selectedDefectId: String? = null,
    onDefectTap: ((CardDefect) -> Unit)? = null,
    focus: NormalizedRect? = null,
    regions: List<NormalizedRect> = emptyList(),
    showCenteringGuides: Boolean = false,
) {
    val aspect = if (focus == null) CardGeometry.STANDARD_ASPECT_RATIO.toFloat() else 1f
    val tapModifier = if (onDefectTap != null && focus == null) {
        Modifier.pointerInput(defects) {
            detectTapGestures { tap ->
                val t = transformFor(Size(size.width.toFloat(), size.height.toFloat()), null)
                val p = t.toCard(tap)
                defects.lastOrNull { markerRect(it.location.boundingBox, t.cardSize).contains(p) }?.let(onDefectTap)
            }
        }
    } else {
        Modifier
    }
    Canvas(
        modifier = modifier
            .aspectRatio(aspect)
            // Zoomed (focus) drawing is scaled beyond the canvas; without clipping it paints over siblings.
            .clipToBounds()
            .semantics { contentDescription = "Card illustration with ${defects.size} marked potential defects" }
            .then(tapModifier),
    ) {
        val t = transformFor(size, focus)
        withTransform({
            translate(t.translate.x, t.translate.y)
            scale(t.scale, t.scale, pivot = Offset.Zero)
        }) {
            drawCard(t.cardSize, t.scale, showCenteringGuides)
            regions.forEach { r ->
                drawRect(
                    color = RegionColor,
                    topLeft = Offset(r.left.toFloat() * t.cardSize.width, r.top.toFloat() * t.cardSize.height),
                    size = Size(r.width.toFloat() * t.cardSize.width, r.height.toFloat() * t.cardSize.height),
                    style = Stroke(width = 1.5.dp.toPx() / t.scale),
                )
            }
            defects.forEach { drawDefect(it, t, selected = it.id == selectedDefectId) }
        }
    }
}

private fun DrawScope.drawCard(card: Size, scale: Float, guides: Boolean) {
    drawRoundRect(CardBody, size = card, cornerRadius = CornerRadius(card.width * 0.045f))
    val insetX = (ART_INSET * card.width).toFloat()
    val insetY = insetX
    val art = Rect(insetX, insetY, card.width - insetX, card.height * 0.55f)
    drawRect(CardArt, topLeft = art.topLeft, size = art.size)
    // Diagonal hatch marks the art box as an illustration placeholder, not a photo.
    clipRect(art.left, art.top, art.right, art.bottom) {
        var x = art.left - art.height
        while (x < art.right) {
            drawLine(CardArtLines, Offset(x, art.top), Offset(x + art.height, art.bottom), strokeWidth = 1f / scale)
            x += card.width * 0.06f
        }
    }
    drawRect(CardArt, topLeft = Offset(insetX, card.height * 0.60f), size = Size(card.width - 2 * insetX, card.height * 0.32f))
    if (guides) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f / scale, 6f / scale))
        val w = 1.5.dp.toPx() / scale
        drawLine(GuideColor, Offset(insetX, 0f), Offset(insetX, card.height), w, pathEffect = dash)
        drawLine(GuideColor, Offset(card.width - insetX, 0f), Offset(card.width - insetX, card.height), w, pathEffect = dash)
        drawLine(GuideColor, Offset(0f, insetY), Offset(card.width, insetY), w, pathEffect = dash)
        drawLine(GuideColor, Offset(0f, card.height - insetY), Offset(card.width, card.height - insetY), w, pathEffect = dash)
    }
}

private fun DrawScope.drawDefect(defect: CardDefect, t: DiagramTransform, selected: Boolean) {
    val rect = markerRect(defect.location.boundingBox, t.cardSize)
    val color = severityColor(defect.severity)
    val experimental = defect.maturity != DetectionMaturity.SUPPORTED
    if (selected) {
        drawRect(color.copy(alpha = 0.3f), topLeft = rect.topLeft, size = rect.size)
    }
    drawRect(
        color = color,
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(
            width = (if (selected) 3.5.dp else 2.5.dp).toPx() / t.scale,
            pathEffect = if (experimental) PathEffect.dashPathEffect(floatArrayOf(10f / t.scale, 6f / t.scale)) else null,
        ),
    )
}
