package com.cardpregrade.core.cv.geometry

import com.cardpregrade.core.model.CardCorner
import com.cardpregrade.core.model.CardEdge
import com.cardpregrade.core.model.NormalizedPoint
import com.cardpregrade.core.model.NormalizedRect
import kotlin.math.roundToInt

/** Physical dimensions shared by Pokemon and One Piece standard-size cards. */
object CardGeometry {
    const val STANDARD_WIDTH_MM = 63.0
    const val STANDARD_HEIGHT_MM = 88.0
    const val STANDARD_ASPECT_RATIO = STANDARD_WIDTH_MM / STANDARD_HEIGHT_MM

    /** Output size for perspective correction that preserves the physical aspect ratio. */
    fun correctedSize(widthPx: Int): PixelSize =
        PixelSize(widthPx, (widthPx / STANDARD_ASPECT_RATIO).roundToInt())
}

data class PixelSize(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "Size must be positive: ${width}x$height" }
    }
}

/** Integer pixel rectangle, right/bottom exclusive. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Deterministic crop regions on a corrected card, expressed in normalized coordinates.
 * Crop sizes are parameters, not tuned constants; they will be calibrated in Phase 5.
 */
object CardRegions {

    /** Square-ish crop at a corner; [fraction] is the crop size as a fraction of card width. */
    fun cornerRegion(corner: CardCorner, fraction: Double, aspectRatio: Double = CardGeometry.STANDARD_ASPECT_RATIO): NormalizedRect {
        require(fraction > 0 && fraction <= 0.5) { "Corner fraction must be in (0, 0.5]" }
        val w = fraction
        // Same physical size vertically: normalized height = fraction * (width / height).
        val h = fraction * aspectRatio
        return when (corner) {
            CardCorner.TOP_LEFT -> NormalizedRect(0.0, 0.0, w, h)
            CardCorner.TOP_RIGHT -> NormalizedRect(1.0 - w, 0.0, 1.0, h)
            CardCorner.BOTTOM_RIGHT -> NormalizedRect(1.0 - w, 1.0 - h, 1.0, 1.0)
            CardCorner.BOTTOM_LEFT -> NormalizedRect(0.0, 1.0 - h, w, 1.0)
        }
    }

    /**
     * Band along an edge, excluding the corner crops at each end so corner damage is not
     * double-counted as edge damage. [bandFraction] is the band depth as a fraction of card width.
     */
    fun edgeRegion(
        edge: CardEdge,
        bandFraction: Double,
        cornerFraction: Double,
        aspectRatio: Double = CardGeometry.STANDARD_ASPECT_RATIO,
    ): NormalizedRect {
        require(bandFraction > 0 && bandFraction < 0.5) { "Band fraction must be in (0, 0.5)" }
        require(cornerFraction >= 0 && cornerFraction < 0.5) { "Corner fraction must be in [0, 0.5)" }
        val bandX = bandFraction
        val bandY = bandFraction * aspectRatio
        val cornerX = cornerFraction
        val cornerY = cornerFraction * aspectRatio
        return when (edge) {
            CardEdge.TOP -> NormalizedRect(cornerX, 0.0, 1.0 - cornerX, bandY)
            CardEdge.BOTTOM -> NormalizedRect(cornerX, 1.0 - bandY, 1.0 - cornerX, 1.0)
            CardEdge.LEFT -> NormalizedRect(0.0, cornerY, bandX, 1.0 - cornerY)
            CardEdge.RIGHT -> NormalizedRect(1.0 - bandX, cornerY, 1.0, 1.0 - cornerY)
        }
    }
}

/** Maps between normalized card coordinates and pixel coordinates of an image of [size]. */
class CoordinateMapper(private val size: PixelSize) {

    fun toPixels(point: NormalizedPoint): Pair<Double, Double> =
        point.x * size.width to point.y * size.height

    /** Converts to a pixel rect that fully covers the normalized rect, clamped to the image. */
    fun toPixels(rect: NormalizedRect): PixelRect = PixelRect(
        left = floorClamp(rect.left * size.width, size.width),
        top = floorClamp(rect.top * size.height, size.height),
        right = ceilClamp(rect.right * size.width, size.width),
        bottom = ceilClamp(rect.bottom * size.height, size.height),
    )

    fun toNormalized(rect: PixelRect): NormalizedRect = NormalizedRect(
        left = rect.left.toDouble() / size.width,
        top = rect.top.toDouble() / size.height,
        right = rect.right.toDouble() / size.width,
        bottom = rect.bottom.toDouble() / size.height,
    )

    private fun floorClamp(v: Double, max: Int) = kotlin.math.floor(snap(v)).toInt().coerceIn(0, max)
    private fun ceilClamp(v: Double, max: Int) = kotlin.math.ceil(snap(v)).toInt().coerceIn(0, max)

    /** Removes floating-point noise (e.g. 62.9999999) so exact boundaries don't shift a pixel. */
    private fun snap(v: Double): Double {
        val rounded = kotlin.math.round(v)
        return if (kotlin.math.abs(v - rounded) < SNAP_EPSILON) rounded else v
    }

    private companion object {
        const val SNAP_EPSILON = 1e-6
    }
}
