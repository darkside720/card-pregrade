package com.cardpregrade.app.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.cardpregrade.core.cv.detection.BoundaryDetection
import com.cardpregrade.core.cv.detection.CardBoundaryDetector
import com.cardpregrade.core.cv.detection.CardDetectionPolicy
import com.cardpregrade.core.cv.image.GrayImage
import com.cardpregrade.core.model.ImageOrientation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** Pure size arithmetic for the detection working image (JVM-testable, no Android types). */
object WorkingImageSizing {
    /** Long side of the upright image the detector and the developer overlay work on. */
    const val TARGET_LONG_SIDE = 1024

    /**
     * Largest power-of-two `inSampleSize` that keeps the decoded long side at or above [target]:
     * starting from 1, doubles while `longSide / (2·sample) ≥ target` (integer division). The
     * decoder's output is at least `longSide / sample`, so the exact downscale that follows never
     * has to upscale. Sources smaller than [target] decode at 1.
     */
    fun sampleSize(longSide: Int, target: Int = TARGET_LONG_SIDE): Int {
        require(longSide > 0 && target > 0) { "Sizes must be positive: $longSide, $target" }
        var sample = 1
        while (longSide / (sample * 2L) >= target) sample *= 2
        return sample
    }

    /**
     * Exact output size for a decoded [width] × [height] bitmap: unchanged if its long side is
     * already ≤ [target] (never upscaled); otherwise the long side becomes exactly [target] and the
     * short side is `round(short · target / long)` with halves rounded up, in exact integer
     * arithmetic, and at least 1 px.
     */
    fun scaledSize(width: Int, height: Int, target: Int = TARGET_LONG_SIDE): Pair<Int, Int> {
        require(width > 0 && height > 0 && target > 0) { "Sizes must be positive: $width x $height, $target" }
        val long = max(width, height)
        if (long <= target) return width to height
        val short = minOf(width, height)
        val scaledShort = maxOf(1L, (short.toLong() * target + long / 2) / long).toInt()
        return if (width >= height) target to scaledShort else scaledShort to target
    }
}

/**
 * The upright working image for one capture: [bitmap] is what the developer overlay displays and
 * [gray] is the same pixels in BT.601 luma, so detector coordinates are bitmap pixel coordinates.
 */
class WorkingImage(val bitmap: Bitmap, val gray: GrayImage) {
    init {
        require(bitmap.width == gray.width && bitmap.height == gray.height) { "Bitmap and grey image sizes differ" }
    }

    companion object {
        /**
         * Decodes [file] once and returns its upright working bitmap, or null if it cannot be decoded:
         * 1. read the stored size (bounds only);
         * 2. decode with [WorkingImageSizing.sampleSize];
         * 3. scale to [WorkingImageSizing.scaledSize] (filtered) if larger than the target. Sizing
         *    uses the decoder's actual output, so nothing is ever upscaled; because the decoder
         *    rounds subsampled sizes, the short side can differ by 1 px from the stored image's
         *    exact aspect ratio (detector coordinates are working-bitmap pixels and are never mapped
         *    back to stored pixels);
         * 4. apply [orientation] with [OrientedBitmapLoader.orient]. Quarter turns swap the
         *    dimensions exactly, so the upright long side is the target too.
         *
         * Blocking; call on an IO dispatcher.
         */
        fun loadBitmap(file: File, orientation: ImageOrientation, targetLongSide: Int = WorkingImageSizing.TARGET_LONG_SIDE): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = WorkingImageSizing.sampleSize(max(bounds.outWidth, bounds.outHeight), targetLongSide)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(file.path, options) ?: return null
            val (w, h) = WorkingImageSizing.scaledSize(decoded.width, decoded.height, targetLongSide)
            val scaled = if (w == decoded.width && h == decoded.height) {
                decoded
            } else {
                Bitmap.createScaledBitmap(decoded, w, h, true).also { if (it !== decoded) decoded.recycle() }
            }
            return OrientedBitmapLoader.orient(scaled, orientation)
        }

        /** Luma of [bitmap] from a single `getPixels` call (no per-pixel access). CPU-bound. */
        fun grayOf(bitmap: Bitmap): GrayImage {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return GrayImage.fromArgb(bitmap.width, bitmap.height, pixels)
        }
    }
}

/** One capture's working image and the boundary detector's result on it. */
class CaptureBoundaryResult(val image: WorkingImage, val policy: CardDetectionPolicy, val detection: BoundaryDetection)

object CaptureBoundaryDetection {
    /**
     * Loads [file]'s working image on [io], then converts it to grey and runs
     * [CardBoundaryDetector] on [compute]. Null if the file cannot be decoded.
     */
    suspend fun run(
        file: File,
        orientation: ImageOrientation,
        policy: CardDetectionPolicy = CardDetectionPolicy.DEFAULT,
        io: CoroutineDispatcher = Dispatchers.IO,
        compute: CoroutineDispatcher = Dispatchers.Default,
    ): CaptureBoundaryResult? {
        val bitmap = withContext(io) { WorkingImage.loadBitmap(file, orientation) } ?: return null
        return withContext(compute) {
            val image = WorkingImage(bitmap, WorkingImage.grayOf(bitmap))
            CaptureBoundaryResult(image, policy, CardBoundaryDetector.detect(image.gray, policy))
        }
    }
}
