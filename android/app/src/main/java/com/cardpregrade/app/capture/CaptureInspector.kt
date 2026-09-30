package com.cardpregrade.app.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.cardpregrade.core.cv.image.GrayImage
import com.cardpregrade.core.cv.quality.CaptureQualityAnalyzer
import com.cardpregrade.core.model.ImageOrientation
import com.cardpregrade.core.model.ImageQualityResult
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** What the app learns from a saved JPEG: real dimensions, orientation, EXIF metadata and quality. */
data class InspectedCapture(
    val storedWidth: Int,
    val storedHeight: Int,
    val orientation: ImageOrientation,
    val iso: Int?,
    val exposureTimeNs: Long?,
    val focalLengthMm: Float?,
    val flashFired: Boolean?,
    val quality: ImageQualityResult,
)

fun interface CaptureInspector {
    fun inspect(imageId: String, file: File): InspectedCapture
}

/**
 * Reads EXIF and pixel data from a saved capture, then runs the pure-Kotlin
 * [CaptureQualityAnalyzer] on a fixed-size luminance downscale (long side = 1024 px), so the
 * sharpness measure is comparable across phones with different sensor resolutions.
 */
class AndroidCaptureInspector(
    private val analyzer: CaptureQualityAnalyzer = CaptureQualityAnalyzer(),
    private val workingLongSide: Int = WORKING_LONG_SIDE,
) : CaptureInspector {

    override fun inspect(imageId: String, file: File): InspectedCapture {
        val exif = runCatching { ExifInterface(file) }.getOrNull()
        val orientation = ImageOrientation.fromExif(
            exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
                ?.takeIf { it != ExifInterface.ORIENTATION_UNDEFINED },
        )
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight

        // Quality metrics use the central region and are invariant to 90° rotations, so the
        // stored (un-rotated) pixels are measured directly.
        val working = if (width > 0 && height > 0) decodeWorking(file, width, height) else null
        val quality = analyzer.analyze(imageId, max(width, 0), max(height, 0), working)

        return InspectedCapture(
            storedWidth = max(width, 0),
            storedHeight = max(height, 0),
            orientation = orientation,
            iso = exif?.let { e ->
                e.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 }
            },
            exposureTimeNs = exif?.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                ?.takeIf { it > 0 }?.let { (it * 1_000_000_000).roundToLong() },
            focalLengthMm = exif?.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)?.takeIf { it > 0 }?.toFloat(),
            flashFired = exif?.getAttribute(ExifInterface.TAG_FLASH)?.toIntOrNull()?.let { (it and 0x1) == 1 },
            quality = quality,
        )
    }

    private fun decodeWorking(file: File, width: Int, height: Int): GrayImage? {
        val longSide = max(width, height)
        var sample = 1
        while (longSide / (sample * 2) >= workingLongSide) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = workingLongSide.toFloat() / max(decoded.width, decoded.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true)
                .also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        return GrayImage.fromArgb(scaled.width, scaled.height, pixels).also { scaled.recycle() }
    }

    companion object {
        const val WORKING_LONG_SIDE = 1024
    }
}

/**
 * The single supported way to decode a stored capture for display or analysis: applies the
 * recorded EXIF orientation so callers always receive upright pixels (ADR 0011).
 */
object OrientedBitmapLoader {
    fun load(file: File, orientation: ImageOrientation, maxLongSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxLongSide) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        if (orientation.rotationDegrees == 0 && !orientation.mirrored) return decoded
        val matrix = Matrix().apply {
            if (orientation.mirrored) postScale(-1f, 1f)
            postRotate(orientation.rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }
}
