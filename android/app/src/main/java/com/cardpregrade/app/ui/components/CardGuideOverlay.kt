package com.cardpregrade.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import com.cardpregrade.core.cv.geometry.CardGeometry

/** Fraction of the preview's limiting dimension the card guide occupies. */
private const val GUIDE_FILL = 0.78f

/** Computes the guide rectangle (card aspect, centred) for a preview of [size]. Shared with Phase 3 cropping. */
fun cardGuideRect(size: Size): Rect {
    val aspect = CardGeometry.STANDARD_ASPECT_RATIO.toFloat()
    var h = size.height * GUIDE_FILL
    var w = h * aspect
    if (w > size.width * GUIDE_FILL) {
        w = size.width * GUIDE_FILL
        h = w / aspect
    }
    val left = (size.width - w) / 2
    val top = (size.height - h) / 2
    return Rect(left, top, left + w, top + h)
}

/**
 * Card-shaped capture guide: dims everything outside a card-proportioned window and draws
 * corner brackets. Drawn over the CameraX preview in Phase 3.
 */
@Composable
fun CardGuideOverlay(modifier: Modifier = Modifier, guideColor: Color = Color.White) {
    Canvas(modifier) {
        val guide = cardGuideRect(size)
        val radius = CornerRadius(guide.width * 0.045f)
        val hole = Path().apply { addRoundRect(RoundRect(guide, radius)) }
        clipPath(hole, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.55f))
        }
        drawRoundRect(guideColor.copy(alpha = 0.6f), guide.topLeft, guide.size, radius, style = Stroke(width = 1.dp.toPx()))
        val len = guide.width * 0.14f
        val stroke = 4.dp.toPx()
        listOf(
            guide.topLeft to Offset(1f, 1f),
            Offset(guide.right, guide.top) to Offset(-1f, 1f),
            Offset(guide.right, guide.bottom) to Offset(-1f, -1f),
            Offset(guide.left, guide.bottom) to Offset(1f, -1f),
        ).forEach { (corner, dir) ->
            drawLine(guideColor, corner, corner + Offset(len * dir.x, 0f), stroke, StrokeCap.Round)
            drawLine(guideColor, corner, corner + Offset(0f, len * dir.y), stroke, StrokeCap.Round)
        }
    }
}
