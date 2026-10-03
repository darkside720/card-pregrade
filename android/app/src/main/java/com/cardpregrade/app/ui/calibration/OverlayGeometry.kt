package com.cardpregrade.app.ui.calibration

import com.cardpregrade.core.cv.geometry.Line
import com.cardpregrade.core.cv.geometry.PixelPoint
import kotlin.math.max
import kotlin.math.min

/**
 * Where an image of [imageWidth] × [imageHeight] pixels lands inside a view of [viewWidth] ×
 * [viewHeight] px when drawn by `Image(…, contentScale = ContentScale.Fit)` with the default
 * `Alignment.Center`, reproducing that path exactly (verified against the Compose UI 1.7.6
 * bytecode of `PainterNode.draw`, `ContentScale.Fit` and `BiasAlignment.align`):
 *
 * 1. scale `s = min(Vw / W, Vh / H)`, computed in Float;
 * 2. the bitmap is drawn at the unrounded Float size `(W·s, H·s)`;
 * 3. its top-left is `Alignment.Center.align(IntSize(round(W·s), round(H·s)), IntSize(round(Vw), round(Vh)))`,
 *    i.e. `Math.round((round(Vw) − round(W·s)) / 2f)` per axis: a whole-pixel offset, with an odd
 *    leftover rounded half up (not the exact fractional letterbox).
 *
 * So image point (x, y) is drawn at view (offsetX + x·s, offsetY + y·s). Image coordinates use
 * the [PixelPoint] convention (integers are pixel corners, the image spans [0, W] × [0, H]).
 * If Compose changes this placement, this mapping must follow.
 */
data class FitMapping(val scale: Double, val offsetX: Double, val offsetY: Double) {

    fun toViewX(imageX: Double): Double = offsetX + imageX * scale

    fun toViewY(imageY: Double): Double = offsetY + imageY * scale

    companion object {
        fun of(imageWidth: Int, imageHeight: Int, viewWidth: Float, viewHeight: Float): FitMapping {
            require(imageWidth > 0 && imageHeight > 0) { "Image must be non-empty: $imageWidth x $imageHeight" }
            require(viewWidth >= 0f && viewHeight >= 0f) { "View size must be non-negative: $viewWidth x $viewHeight" }
            val scale = min(viewWidth / imageWidth, viewHeight / imageHeight)
            val offsetX = Math.round((Math.round(viewWidth) - Math.round(imageWidth * scale)) / 2f)
            val offsetY = Math.round((Math.round(viewHeight) - Math.round(imageHeight * scale)) / 2f)
            return FitMapping(scale.toDouble(), offsetX.toDouble(), offsetY.toDouble())
        }
    }
}

/** Shortest clipped piece reported as a segment; shorter ones are rounding residue of a touch. */
const val MIN_SEGMENT_PX = 1e-9

fun clipLineToRect(line: Line, width: Double, height: Double): Pair<PixelPoint, PixelPoint>? {
    require(width > 0.0 && height > 0.0) { "Rectangle must be non-empty: $width x $height" }
    val fx = -line.a * line.c
    val fy = -line.b * line.c
    val dx = -line.b
    val dy = line.a
    var tMin = Double.NEGATIVE_INFINITY
    var tMax = Double.POSITIVE_INFINITY
    for ((f, d, upper) in listOf(Triple(fx, dx, width), Triple(fy, dy, height))) {
        if (d == 0.0) {
            if (f < 0.0 || f > upper) return null
        } else {
            val t0 = (0.0 - f) / d
            val t1 = (upper - f) / d
            tMin = max(tMin, min(t0, t1))
            tMax = min(tMax, max(t0, t1))
        }
    }
    // d is a unit vector, so t is arc length in pixels: an interval of at most 1e-9 px is a
    // single touching point (e.g. a corner) up to rounding, not a drawable segment.
    if (tMax - tMin <= MIN_SEGMENT_PX) return null
    // The ends lie on the boundary up to rounding; clamping keeps them exactly inside.
    fun at(t: Double) = PixelPoint((fx + t * dx).coerceIn(0.0, width), (fy + t * dy).coerceIn(0.0, height))
    return at(tMin) to at(tMax)
}
